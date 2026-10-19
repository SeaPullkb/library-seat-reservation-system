package com.seapullkb.library;

import com.seapullkb.library.entity.Role;
import com.seapullkb.library.mapper.ReservationMapper;
import com.seapullkb.library.security.AccountPrincipal;
import com.seapullkb.library.service.*;
import com.seapullkb.library.web.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
@Import(DeliveryIntegrationTest.TimeConfiguration.class)
class DeliveryIntegrationTest {
    static final LocalDate DAY=LocalDate.of(2030,5,8);
    static final String PASSWORD="Delivery-test-123!",NEXT="Changed-student-456!";
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.0").withDatabaseName("delivery_test");
    @DynamicPropertySource static void database(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url",MYSQL::getJdbcUrl);p.add("spring.datasource.username",MYSQL::getUsername);p.add("spring.datasource.password",MYSQL::getPassword);
    }
    static class MutableClock extends Clock {
        final AtomicReference<Instant> value=new AtomicReference<>();
        void at(LocalDateTime value) {this.value.set(value.atZone(getZone()).toInstant());}
        public ZoneId getZone() {return ZoneId.of("Asia/Shanghai");}
        public Clock withZone(ZoneId zone) {return Clock.fixed(instant(),zone);}
        public Instant instant() {return value.get();}
    }
    @TestConfiguration static class TimeConfiguration {
        @Bean @Primary MutableClock deliveryClock() {var c=new MutableClock();c.at(DAY.atTime(7,30));return c;}
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired InitialDataService initial;
    @Autowired AccountService accounts;
    @Autowired StudentProfileService profiles;
    @Autowired UserAdminService users;
    @Autowired AdminAccess access;
    @Autowired ReservationService reservations;
    @Autowired ReservationMapper records;
    @Autowired ReservationMaintenance maintenance;
    @Autowired StatisticsService statistics;
    @Autowired PasswordEncoder passwords;
    @Autowired SessionRegistry sessions;
    @Autowired MutableClock clock;
    Long room,seat,student;
    @BeforeEach void seed() {
        sessions.getAllPrincipals().forEach(p -> sessions.getAllSessions(p,true).forEach(s -> sessions.removeSessionInformation(s.getSessionId())));
        jdbc.update("DELETE FROM reserve_record");jdbc.update("DELETE FROM seat");jdbc.update("DELETE FROM reading_room");jdbc.update("DELETE FROM `user`");
        clock.at(DAY.atTime(7,30));initial.initialize(true,PASSWORD);
        room=jdbc.queryForObject("SELECT MIN(id) FROM reading_room",Long.class);seat=jdbc.queryForObject("SELECT MIN(id) FROM seat",Long.class);
        student=accounts.findByUsername("student").getId();
    }
    MockHttpSession login(String name,String password) throws Exception {
        return (MockHttpSession)mvc.perform(formLogin().user(name).password(password)).andExpect(authenticated()).andReturn().getRequest().getSession(false);
    }
    @Test void profileReadsOwnPublicFieldsAndIgnoresForgedIdentityAndPrivileges() throws Exception {
        var session=login("student",PASSWORD);String hash=accounts.findByUsername("student").getPassword();
        mvc.perform(get("/student/profile").session(session).param("id",accounts.findByUsername("admin").getId().toString()))
                .andExpect(status().isOk()).andExpect(content().string(containsString("个人中心")))
                .andExpect(content().string(not(containsString(hash)))).andExpect(content().string(not(containsString(PASSWORD))));
        mvc.perform(post("/student/profile").session(session).with(csrf()).param("realName","  新姓名  ").param("className","毕业班").param("phone","13912345678")
                .param("id",accounts.findByUsername("admin").getId().toString()).param("username","admin").param("password",NEXT).param("role","SUPER_ADMIN").param("status","0"))
                .andExpect(redirectedUrl("/student/profile"));
        var user=accounts.findByUsername("student");assertThat(user.getRealName()).isEqualTo("新姓名");assertThat(user.getRole()).isEqualTo("STUDENT");assertThat(user.getPassword()).isEqualTo(hash);assertThat(user.getStatus()).isEqualTo(1);
        assertThat(accounts.findByUsername("admin").getRealName()).isEqualTo("系统管理员");
    }
    @ParameterizedTest @ValueSource(strings={"", "<script>alert(1)</script>"})
    void invalidProfileDoesNotMutateAndDisplayEscapesText(String name) throws Exception {
        var request=post("/student/profile").with(user("student").roles("STUDENT")).with(csrf()).param("realName",name).param("className","毕业班").param("phone",name.isEmpty()?"bad":"13912345678");
        if(name.isEmpty()) {
            mvc.perform(request).andExpect(status().isOk()).andExpect(model().hasErrors());
            assertThat(accounts.findByUsername("student").getRealName()).isEqualTo("演示学生");
        } else {
            mvc.perform(request).andExpect(status().is3xxRedirection());
            mvc.perform(get("/student/profile").with(user("student").roles("STUDENT"))).andExpect(status().isOk())
                    .andExpect(content().string(containsString("&lt;script&gt;alert(1)&lt;/script&gt;"))).andExpect(content().string(not(containsString("<script>alert(1)</script>"))));
        }
    }
    @Test void selfPasswordChangeVerifiesOldPasswordInvalidatesAllSessionsAndKeepsHistory() throws Exception {
        var first=login("student",PASSWORD);var second=login("student",PASSWORD);Long id=reservations.create("student",room,seat,DAY,1);
        mvc.perform(post("/student/profile/password").session(first).with(csrf()).param("currentPassword",PASSWORD).param("password",NEXT).param("confirmPassword",NEXT))
                .andExpect(redirectedUrl("/login?changed")).andExpect(unauthenticated());assertThat(first.isInvalid()).isTrue();
        mvc.perform(get("/student").session(second)).andExpect(redirectedUrl("/login?changed"));
        mvc.perform(formLogin().user("student").password(PASSWORD)).andExpect(unauthenticated());login("student",NEXT);
        assertThat(records.selectById(id).getStatus()).isEqualTo("PENDING");assertThat(passwords.matches(NEXT,accounts.findByUsername("student").getPassword())).isTrue();
    }
    @ParameterizedTest @ValueSource(strings={"wrong", "same", "short", "unicode", "mismatch"})
    void invalidPasswordChangeDoesNotAlterHashOrEchoAnyPassword(String kind) throws Exception {
        String old=kind.equals("wrong")?"Wrong-password-123!":PASSWORD;
        String next=switch(kind) {case "same" -> PASSWORD;case "short" -> "tiny";case "unicode" -> "字".repeat(25);default -> NEXT;};
        String confirm=kind.equals("mismatch")?"different":next;String hash=accounts.findByUsername("student").getPassword();
        mvc.perform(post("/student/profile/password").with(user("student").roles("STUDENT")).with(csrf()).param("currentPassword",old).param("password",next).param("confirmPassword",confirm))
                .andExpect(redirectedUrl("/student/profile")).andExpect(flash().attributeExists("error"));
        assertThat(accounts.findByUsername("student").getPassword()).isEqualTo(hash);
        mvc.perform(get("/student/profile").with(user("student").roles("STUDENT"))).andExpect(content().string(not(containsString(old)))).andExpect(content().string(not(containsString(next))));
    }
    @Test void profileRequiresStudentAndCsrfAndAllMutationRoutesRejectMissingCsrf() throws Exception {
        mvc.perform(get("/student/profile")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/student/profile").with(user("librarian").roles("LIBRARIAN"))).andExpect(status().isForbidden());
        List<String> studentRoutes=List.of("/student/profile","/student/profile/password","/student/reservations/1/checkin","/student/reservations/1/checkout","/student/reservations/1/cancel","/student/rooms/1/seats/1/reservations","/logout");
        for(String path:studentRoutes) mvc.perform(post(path).with(user("student").roles("STUDENT"))).andExpect(status().isForbidden());
        for(String path:List.of("/admin/rooms","/admin/rooms/1","/admin/rooms/1/status","/admin/rooms/1/seats","/admin/rooms/1/seats/1","/admin/rooms/1/seats/1/status","/admin/rooms/1/seats/1/delete","/admin/reservations/1/cancel","/admin/users","/admin/users/1","/admin/users/1/role","/admin/users/1/status","/admin/users/1/password"))
            mvc.perform(post(path).with(user("admin").roles("SUPER_ADMIN"))).andExpect(status().isForbidden());
    }
    @Test void erasedPrincipalStillDetectsPasswordChangeEvenWhenLoginRegisteredAfterRevocation() throws Exception {
        var session=login("student",PASSWORD);
        var context=(org.springframework.security.core.context.SecurityContext)session.getAttribute("SPRING_SECURITY_CONTEXT");
        var principal=(AccountPrincipal)context.getAuthentication().getPrincipal();assertThat(principal.getPassword()).isNull();
        assertThat(principal.matchesPasswordStamp(accounts.findByUsername("student").getPassword())).isTrue();
        // Simulates an in-flight old-password login missed by SessionRegistry.expire.
        sessions.removeSessionInformation(session.getId());
        users.resetPassword("admin",student,new PasswordResetForm(NEXT,NEXT));
        mvc.perform(get("/student/reservations").session(session)).andExpect(status().isForbidden());assertThat(session.isInvalid()).isTrue();
        login("student",NEXT);
    }
    @Test void protectedSpaceWriteRechecksRoleUnderUserLock() {
        jdbc.update("UPDATE `user` SET role='STUDENT' WHERE username='librarian'");
        assertThatThrownBy(() -> access.write("librarian",() -> jdbc.update("UPDATE reading_room SET room_name='bad'"))).isInstanceOf(AccessDeniedException.class);
        assertThat(jdbc.queryForObject("SELECT room_name FROM reading_room WHERE id=?",String.class,room)).isEqualTo("第一阅览室");
    }
    @ParameterizedTest @ValueSource(strings={"0999-12-31", "+10000-01-01"})
    void unsupportedMysqlDatesReturnSafeBadRequest(String date) throws Exception {
        for(String path:List.of("/admin/statistics","/admin/reservations")) mvc.perform(get(path).with(user("admin").roles("SUPER_ADMIN")).param("date",date))
                .andExpect(status().isBadRequest()).andExpect(content().string(not(containsString("SQLException"))));
    }
    @Test void missingParametersUnknownPathsAndWrongMethodHaveSafeErrors() throws Exception {
        mvc.perform(post("/admin/users/"+student+"/status").with(user("admin").roles("SUPER_ADMIN")).with(csrf()))
                .andExpect(status().isBadRequest()).andExpect(content().string(containsString("参数无效")));
        mvc.perform(get("/missing-page").with(user("student").roles("STUDENT"))).andExpect(status().isNotFound());
        mvc.perform(get("/student/profile/password").with(user("student").roles("STUDENT"))).andExpect(status().isMethodNotAllowed()).andExpect(header().string("Allow",containsString("POST")));
        mvc.perform(get("/admin/users/999999").with(user("admin").roles("SUPER_ADMIN"))).andExpect(status().isNotFound()).andExpect(content().string(not(containsString("stacktrace"))));
    }
    @Test void pagesHaveStrictScriptsSecurityHeadersAndSafeChartData() throws Exception {
        mvc.perform(get("/login")).andExpect(header().string("Content-Security-Policy",containsString("script-src 'self'")))
                .andExpect(header().string("X-Content-Type-Options","nosniff")).andExpect(header().string("X-Frame-Options","DENY"));
        mvc.perform(get("/admin/statistics").with(user("admin").roles("SUPER_ADMIN")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("type=\"application/json\"")))
                .andExpect(content().string(not(containsString("<script>window"))));
        mvc.perform(get("/student/profile").with(user("student").roles("STUDENT"))).andExpect(content().string(containsString("功能导航"))).andExpect(content().string(containsString("跳至主要内容")));
    }
    @Test void registrationToProfileBookingCheckinCheckoutAndStatisticsIsOneCompleteWorkflow() throws Exception {
        mvc.perform(post("/register").with(csrf()).param("username","graduate_student").param("password",PASSWORD).param("confirmPassword",PASSWORD)
                .param("realName","毕业验收同学").param("className","验收班").param("phone","13912345678")).andExpect(redirectedUrl("/login"));
        var session=login("graduate_student",PASSWORD);
        mvc.perform(post("/student/profile").session(session).with(csrf()).param("realName","完整流程同学").param("className","毕业班").param("phone","13912345678")).andExpect(status().is3xxRedirection());
        mvc.perform(post("/student/rooms/"+room+"/seats/"+seat+"/reservations").session(session).with(csrf()).param("date",DAY.toString()).param("slot","1")).andExpect(redirectedUrl("/student/reservations"));
        Long id=jdbc.queryForObject("SELECT MAX(id) FROM reserve_record",Long.class);clock.at(DAY.atTime(8,0));
        mvc.perform(post("/student/reservations/"+id+"/checkin").session(session).with(csrf())).andExpect(flash().attribute("notice",containsString("签到成功")));
        clock.at(DAY.atTime(9,0));mvc.perform(post("/student/reservations/"+id+"/checkout").session(session).with(csrf())).andExpect(flash().attribute("notice",containsString("签退成功")));
        var d=statistics.dashboard("librarian",DAY,1);assertThat(d.daily().students()).isEqualTo(1);assertThat(d.daily().reservations()).isEqualTo(1);assertThat(d.daily().getCheckinRate()).isEqualTo(100.0);assertThat(d.rooms().get(0).occupiedSeats()).isEqualTo(1);
        mvc.perform(get("/admin/reservations").with(user("librarian").roles("LIBRARIAN"))).andExpect(status().isOk()).andExpect(content().string(containsString("完整流程同学"))).andExpect(content().string(containsString("已完成")));
    }
    @Test void simultaneousPasswordUpdatesAlwaysRevalidateCurrentHash() throws Exception {
        var pool=Executors.newFixedThreadPool(2);var ready=new CountDownLatch(2);var start=new CountDownLatch(1);
        try {
            var self=pool.submit(() -> {ready.countDown();start.await();try {profiles.password("student",new StudentPasswordForm(PASSWORD,NEXT,NEXT));}catch(MaintenanceConflictException expected) {}return true;});
            var reset=pool.submit(() -> {ready.countDown();start.await();users.resetPassword("admin",student,new PasswordResetForm("Admin-reset-123!","Admin-reset-123!"));return true;});
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue();start.countDown();self.get(20,TimeUnit.SECONDS);assertThat(reset.get(20,TimeUnit.SECONDS)).isTrue();
            assertThat(passwords.matches("Admin-reset-123!",accounts.findByUsername("student").getPassword())).isTrue();
        } finally {pool.shutdownNow();}
    }
}
