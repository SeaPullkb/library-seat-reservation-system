package com.seapullkb.library;

import com.seapullkb.library.entity.*;
import com.seapullkb.library.entity.Role;
import com.seapullkb.library.mapper.ReservationMapper;
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
@Import(AdminIntegrationTest.TimeConfiguration.class)
class AdminIntegrationTest {
    static final LocalDate DAY=LocalDate.of(2030,5,8);
    static final String PASSWORD="Integration-test-123!";
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.0").withDatabaseName("admin_test");
    @DynamicPropertySource static void database(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url",MYSQL::getJdbcUrl);p.add("spring.datasource.username",MYSQL::getUsername);p.add("spring.datasource.password",MYSQL::getPassword);
    }
    static class MutableClock extends Clock {
        final AtomicReference<Instant> value=new AtomicReference<>();
        void at(LocalDateTime date) { value.set(date.atZone(getZone()).toInstant()); }
        public ZoneId getZone() {return ZoneId.of("Asia/Shanghai");}
        public Clock withZone(ZoneId zone) {return Clock.fixed(instant(),zone);}
        public Instant instant() {return value.get();}
    }
    @TestConfiguration static class TimeConfiguration {
        @Bean @Primary MutableClock adminClock() {var c=new MutableClock();c.at(DAY.atTime(7,30));return c;}
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired InitialDataService initial;
    @Autowired AccountService accounts;
    @Autowired SpaceService spaces;
    @Autowired ReservationService reservations;
    @Autowired ReservationLifecycleService lifecycle;
    @Autowired ReservationMaintenance maintenance;
    @Autowired AdminReservationService adminReservations;
    @Autowired UserAdminService users;
    @Autowired StatisticsService statistics;
    @Autowired ReservationMapper records;
    @Autowired PasswordEncoder passwords;
    @Autowired SessionRegistry sessions;
    @Autowired MutableClock clock;
    Long room,seat,secondSeat,student,admin;
    @BeforeEach void seed() {
        sessions.getAllPrincipals().forEach(p -> sessions.getAllSessions(p,true).forEach(s -> sessions.removeSessionInformation(s.getSessionId())));
        jdbc.update("DELETE FROM reserve_record");jdbc.update("DELETE FROM seat");jdbc.update("DELETE FROM reading_room");jdbc.update("DELETE FROM `user`");
        clock.at(DAY.atTime(7,30));initial.initialize(true,PASSWORD);
        accounts.registerStudent(new RegistrationForm("second",PASSWORD,PASSWORD,"二同学","二班","13912345678"));
        room=spaces.listRooms(false).get(0).id();seat=spaces.listSeats(room).get(0).getId();secondSeat=spaces.listSeats(room).get(1).getId();
        student=accounts.findByUsername("student").getId();admin=accounts.findByUsername("admin").getId();
    }
    Long book() {return reservations.create("student",room,seat,DAY,1);}
    AdminReservationFilter filter(String keyword,Long roomId,LocalDate date,Integer slot,ReservationStatus status) {return new AdminReservationFilter(keyword,roomId,date,slot,status);}
    MockHttpSession login(String username) throws Exception {
        return (MockHttpSession)mvc.perform(formLogin().user(username).password(PASSWORD)).andExpect(authenticated()).andReturn().getRequest().getSession(false);
    }
    Long raw(Long user,Long targetSeat,LocalDate date,int slot,String status,LocalDateTime deadline,LocalDateTime checkin) {
        jdbc.update("INSERT INTO reserve_record(user_id,seat_id,reserve_date,time_slot,status,checkin_deadline,checkin_at,created_at) VALUES (?,?,?,?,?,?,?,?)",user,targetSeat,date,slot,status,deadline,checkin,date.atTime(7,0));
        return jdbc.queryForObject("SELECT MAX(id) FROM reserve_record",Long.class);
    }
    @Test void adminSearchFiltersLiteralKeywordAndEffectiveStatusWithoutWriting() throws Exception {
        Long id=book();clock.at(DAY.atTime(8,16));
        var page=adminReservations.search("librarian",filter("student",room,DAY,1,ReservationStatus.EXPIRED),0);
        assertThat(page.total()).isEqualTo(1);assertThat(page.records().get(0).status()).isEqualTo("EXPIRED");
        assertThat(records.selectById(id).getStatus()).isEqualTo("PENDING");
        assertThat(adminReservations.search("admin",filter("%",null,null,null,null),0).total()).isZero();
        assertThat(adminReservations.search("admin",filter("student",room,DAY,2,null),0).total()).isZero();
        mvc.perform(get("/admin/reservations").with(user("librarian").roles("LIBRARIAN")).param("status","EXPIRED").param("date",DAY.toString()))
                .andExpect(status().isOk()).andExpect(content().string(containsString("超时释放")));
    }
    @Test void stablePaginationHasNoDuplicatesAndRetainsIsoDate() throws Exception {
        for(int i=0;i<23;i++) raw(student,seat,DAY,1,"CANCELLED",DAY.atTime(8,15),null);
        var f=filter("",room,DAY,1,null);var first=adminReservations.search("admin",f,0);var second=adminReservations.search("admin",f,1);
        assertThat(first.records()).hasSize(20);assertThat(second.records()).hasSize(3);
        assertThat(first.records().stream().map(AdminReservationRow::id)).doesNotContainAnyElementsOf(second.records().stream().map(AdminReservationRow::id).toList());
        mvc.perform(get("/admin/reservations").with(user("admin").roles("SUPER_ADMIN")).param("date",DAY.toString()))
                .andExpect(status().isOk()).andExpect(content().string(containsString("date=2030-05-08")));
    }
    @Test void adminMayCancelAfterStartBeforeDeadlineAndKeepsFirstAuditOnRepeat() {
        Long id=book();clock.at(DAY.atTime(8,5));
        assertThat(adminReservations.cancel("librarian",id,"  占座违规  ")).isEqualTo(LifecycleResult.CANCELLED);
        var r=records.selectById(id);assertThat(r.getStatus()).isEqualTo("CANCELLED");assertThat(r.getCancelledBy()).isEqualTo(accounts.findByUsername("librarian").getId());
        assertThat(r.getCancelReason()).isEqualTo("占座违规");assertThat(r.getCancelledAt()).isEqualTo(DAY.atTime(8,5));
        clock.at(DAY.atTime(8,6));assertThat(adminReservations.cancel("admin",id,"第二次")).isEqualTo(LifecycleResult.NONE);
        assertThat(records.selectById(id).getCancelledBy()).isEqualTo(r.getCancelledBy());assertThat(records.selectById(id).getCancelledAt()).isEqualTo(r.getCancelledAt());
        assertThat(reservations.create("second",room,seat,DAY,1)).isNotNull();
    }
    @Test void invalidReasonCannotMutateOrBeUsedToInjectHtml() throws Exception {
        Long id=book();
        for(String reason:List.of(" ","字".repeat(256))) assertThatThrownBy(() -> adminReservations.cancel("admin",id,reason)).isInstanceOf(IllegalArgumentException.class);
        assertThat(records.selectById(id).getStatus()).isEqualTo("PENDING");
        mvc.perform(post("/admin/reservations/"+id+"/cancel").with(user("admin").roles("SUPER_ADMIN")).with(csrf()).param("reason","<script>alert(1)</script>"))
                .andExpect(redirectedUrl("/admin/reservations"));
        mvc.perform(get("/admin/reservations").with(user("admin").roles("SUPER_ADMIN")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("&lt;script&gt;alert(1)&lt;/script&gt;")))
                .andExpect(content().string(not(containsString("<script>alert(1)</script>"))));
    }
    @Test void adminCancellationExpiresOverdueBeforeReportingConflict() {
        Long id=book();clock.at(DAY.atTime(8,15,1));
        assertThatThrownBy(() -> adminReservations.cancel("admin",id,"违规")).isInstanceOf(ReservationConflictException.class);
        assertThat(records.selectById(id).getStatus()).isEqualTo("EXPIRED");assertThat(records.selectById(id).getCancelledAt()).isNull();
    }
    @ParameterizedTest @ValueSource(strings={"USING","COMPLETED","EXPIRED"})
    void usedOrExpiredRecordsCannotBeAdminCancelled(String state) {
        Long id=book();jdbc.update("UPDATE reserve_record SET status=?,checkin_at=? WHERE id=?",state,state.equals("EXPIRED")?null:DAY.atTime(8,0),id);
        assertThatThrownBy(() -> adminReservations.cancel("admin",id,"违规")).isInstanceOf(ReservationConflictException.class);
        assertThat(records.selectById(id).getStatus()).isEqualTo(state);
    }
    @Test void administrativeRoutesEnforceRoleAndCsrf() throws Exception {
        Long id=book();
        for(String path:List.of("/admin/reservations","/admin/statistics","/admin/users")) {
            mvc.perform(get(path)).andExpect(status().is3xxRedirection());
            mvc.perform(get(path).with(user("student").roles("STUDENT"))).andExpect(status().isForbidden());
        }
        for(String suffix:List.of("","/new","/"+student)) mvc.perform(get("/admin/users"+suffix).with(user("librarian").roles("LIBRARIAN"))).andExpect(status().isForbidden());
        mvc.perform(post("/admin/reservations/"+id+"/cancel").with(user("librarian").roles("LIBRARIAN")).param("reason","违规")).andExpect(status().isForbidden());
        mvc.perform(post("/admin/users/"+student+"/status").with(user("admin").roles("SUPER_ADMIN")).param("enabled","false")).andExpect(status().isForbidden());
        assertThat(records.selectById(id).getStatus()).isEqualTo("PENDING");
    }
    @Test void serviceRechecksDisabledAndDowngradedOperators() {
        Long id=book();jdbc.update("UPDATE `user` SET role='STUDENT' WHERE username='librarian'");
        assertThatThrownBy(() -> adminReservations.cancel("librarian",id,"违规")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> users.enabled("librarian",student,false)).isInstanceOf(AccessDeniedException.class);
        jdbc.update("UPDATE `user` SET status=0 WHERE username='admin'");
        assertThatThrownBy(() -> users.enabled("admin",student,false)).isInstanceOf(AccessDeniedException.class);
    }
    @Test void createsOnlyEnabledLibrarianAndNeverEchoesPasswords() throws Exception {
        mvc.perform(post("/admin/users").with(user("admin").roles("SUPER_ADMIN")).with(csrf())
                .param("username","new_librarian").param("password",PASSWORD).param("realName","新管理员").param("phone","13912345678").param("role","SUPER_ADMIN").param("status","0"))
                .andExpect(status().is3xxRedirection());
        var u=accounts.findByUsername("new_librarian");assertThat(u.getRole()).isEqualTo("LIBRARIAN");assertThat(u.getStatus()).isEqualTo(1);
        assertThat(passwords.matches(PASSWORD,u.getPassword())).isTrue();
        mvc.perform(get("/admin/users/"+u.getId()).with(user("admin").roles("SUPER_ADMIN"))).andExpect(status().isOk())
                .andExpect(content().string(not(containsString(u.getPassword())))).andExpect(content().string(not(containsString(PASSWORD))));
    }
    @Test void duplicateAndInvalidAdministratorFormsPreserveExistingAccountAndDoNotEchoInput() throws Exception {
        String hash=accounts.findByUsername("student").getPassword();
        mvc.perform(post("/admin/users").with(user("admin").roles("SUPER_ADMIN")).with(csrf()).param("username","STUDENT")
                .param("password",PASSWORD).param("realName","新管理").param("phone","13912345678"))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("adminCreateForm","username"))
                .andExpect(content().string(not(containsString(PASSWORD))));
        assertThat(accounts.findByUsername("student").getPassword()).isEqualTo(hash);
        mvc.perform(post("/admin/users").with(user("admin").roles("SUPER_ADMIN")).with(csrf()).param("username","new_admin")
                .param("password","字".repeat(25)).param("realName","新管理").param("phone","13912345678"))
                .andExpect(status().isOk()).andExpect(model().hasErrors());
        assertThat(accounts.findByUsername("new_admin")).isNull();
    }
    @Test void profileEditsCannotChangeRoleUsernamePasswordOrStatus() throws Exception {
        String hash=accounts.findByUsername("student").getPassword();
        mvc.perform(post("/admin/users/"+student).with(user("admin").roles("SUPER_ADMIN")).with(csrf()).param("realName","  新姓名  ")
                .param("className","新班级").param("phone","13912345678").param("username","hacked").param("role","SUPER_ADMIN").param("status","0").param("password","hacked"))
                .andExpect(redirectedUrl("/admin/users/"+student));
        var u=accounts.findByUsername("student");assertThat(u.getRealName()).isEqualTo("新姓名");assertThat(u.getRole()).isEqualTo("STUDENT");assertThat(u.getStatus()).isEqualTo(1);assertThat(u.getPassword()).isEqualTo(hash);
    }
    @Test void userFilteringAndPaginationExcludePasswordField() throws Exception {
        for(int i=0;i<21;i++) jdbc.update("INSERT INTO `user`(username,password,real_name,phone,role) SELECT ?,password,'分页用户','13912345678','STUDENT' FROM `user` WHERE id=?","fixture_"+i,student);
        var a=users.search("admin","fixture_",Role.STUDENT,1,0);var b=users.search("admin","fixture_",Role.STUDENT,1,1);
        assertThat(a.total()).isEqualTo(21);assertThat(a.records()).hasSize(20);assertThat(b.records()).hasSize(1);
        assertThat(a.records().stream().map(UserSummary::id)).doesNotContain(b.records().get(0).id());
        assertThat(users.search("admin","%",null,null,0).total()).isZero();
        mvc.perform(get("/admin/users").with(user("admin").roles("SUPER_ADMIN")).param("keyword","fixture_").param("role","STUDENT"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("page=1")))
                .andExpect(content().string(not(containsString(accounts.findByUsername("student").getPassword()))));
    }
    @Test void pendingAndUsingReservationsBlockDisableAndRoleChangeUntilResolved() {
        Long id=book();
        assertThatThrownBy(() -> users.enabled("admin",student,false)).isInstanceOf(MaintenanceConflictException.class);
        assertThatThrownBy(() -> users.role("admin",student,Role.LIBRARIAN)).isInstanceOf(MaintenanceConflictException.class);
        clock.at(DAY.atTime(8,0));lifecycle.checkIn("student",id);
        assertThatThrownBy(() -> users.enabled("admin",student,false)).isInstanceOf(MaintenanceConflictException.class);
        lifecycle.checkOut("student",id);users.role("admin",student,Role.LIBRARIAN);users.enabled("admin",student,false);
        assertThat(accounts.findByUsername("student").getRole()).isEqualTo("LIBRARIAN");assertThat(accounts.findByUsername("student").getStatus()).isZero();
        assertThat(records.selectById(id).getStatus()).isEqualTo("COMPLETED");
    }
    @Test void selfDisableAndSelfDemotionCannotRemoveLastEnabledSuperAdmin() {
        assertThatThrownBy(() -> users.enabled("admin",admin,false)).isInstanceOf(MaintenanceConflictException.class);
        assertThatThrownBy(() -> users.role("admin",admin,Role.STUDENT)).isInstanceOf(MaintenanceConflictException.class);
        assertThat(accounts.findByUsername("admin").getStatus()).isEqualTo(1);
    }
    @Test void passwordResetRevokesAllOldSessionsAndOldPasswordStopsWorking() throws Exception {
        var first=login("student");var second=login("student");String next="Reset-password-456!";
        mvc.perform(post("/admin/users/"+student+"/password").with(user("admin").roles("SUPER_ADMIN")).with(csrf()).param("password",next).param("confirmPassword",next))
                .andExpect(redirectedUrl("/admin/users/"+student));
        mvc.perform(get("/student").session(first)).andExpect(redirectedUrl("/login?changed"));
        mvc.perform(get("/student").session(second)).andExpect(redirectedUrl("/login?changed"));
        mvc.perform(formLogin().user("student").password(PASSWORD)).andExpect(unauthenticated());
        mvc.perform(formLogin().user("student").password(next)).andExpect(authenticated());
        assertThat(passwords.matches(next,accounts.findByUsername("student").getPassword())).isTrue();
    }
    @Test void invalidResetKeepsHashAndDoesNotReturnPassword() throws Exception {
        String hash=accounts.findByUsername("student").getPassword();
        for(String next:List.of("short","字".repeat(25))) mvc.perform(post("/admin/users/"+student+"/password").with(user("admin").roles("SUPER_ADMIN")).with(csrf())
                .param("password",next).param("confirmPassword",next)).andExpect(flash().attributeExists("error"));
        assertThat(accounts.findByUsername("student").getPassword()).isEqualTo(hash);
    }
    @Test void roleChangeAndDisableExpireRealSessionsAndNewLoginUsesNewRole() throws Exception {
        var old=login("librarian");Long librarian=accounts.findByUsername("librarian").getId();
        users.role("admin",librarian,Role.STUDENT);
        mvc.perform(get("/admin/rooms").session(old)).andExpect(redirectedUrl("/login?changed"));
        var changed=login("librarian");mvc.perform(get("/admin").session(changed)).andExpect(status().isForbidden());
        users.enabled("admin",librarian,false);
        mvc.perform(get("/student").session(changed)).andExpect(redirectedUrl("/login?changed"));
        mvc.perform(formLogin().user("librarian").password(PASSWORD)).andExpect(unauthenticated());
        users.enabled("admin",librarian,true);login("librarian");
    }
    @Test void outOfBandRoleChangeStillRejectsStaleSession() throws Exception {
        var old=login("librarian");jdbc.update("UPDATE `user` SET role='STUDENT' WHERE username='librarian'");
        mvc.perform(get("/admin/rooms").session(old)).andExpect(status().isForbidden());assertThat(old.isInvalid()).isTrue();
    }
    @Test void invalidParametersAndUnknownUsersReturnControlledErrors() throws Exception {
        for(String path:List.of("/admin/users?status=9","/admin/users?page=-1","/admin/users?role=INVALID","/admin/reservations?slot=9","/admin/reservations?date=bad","/admin/statistics?slot=0"))
            mvc.perform(get(path).with(user("admin").roles("SUPER_ADMIN"))).andExpect(status().isBadRequest());
        mvc.perform(get("/admin/users/999999").with(user("admin").roles("SUPER_ADMIN"))).andExpect(status().isNotFound());
    }
    @Test void statisticsUseCorrectDistinctCountsDeadlineDenominatorAndAllStates() {
        clock.at(DAY.atTime(10,0));Long other=accounts.findByUsername("second").getId();
        raw(student,seat,DAY,1,"COMPLETED",DAY.atTime(8,15),DAY.atTime(8,0));
        raw(student,seat,DAY,1,"EXPIRED",DAY.atTime(8,15),null);
        raw(other,secondSeat,DAY,1,"USING",DAY.atTime(8,15),DAY.atTime(8,0));
        raw(admin,secondSeat,DAY,1,"CANCELLED",DAY.atTime(8,15),null);
        raw(other,secondSeat,DAY,2,"PENDING",DAY.atTime(14,15),null);
        raw(other,secondSeat,DAY,1,"PENDING",DAY.atTime(10,0),null);
        var d=statistics.dashboard("librarian",DAY,1);assertThat(d.daily().reservations()).isEqualTo(6);assertThat(d.daily().students()).isEqualTo(2);
        assertThat(d.daily().eligible()).isEqualTo(3);assertThat(d.daily().checkedIn()).isEqualTo(2);assertThat(d.daily().getCheckinRate()).isEqualTo(66.67);
        assertThat(d.daily().expired()).isEqualTo(1);assertThat(d.rooms().get(0).occupiedSeats()).isEqualTo(2);assertThat(d.rooms().get(0).enabledSeats()).isEqualTo(20);
        assertThat(d.rooms().get(0).getReservationRate()).isEqualTo(10.0);
    }
    @Test void statisticsDoNotDependOnSweepForOccupancyAndKeepPersistedExpiryCount() {
        Long id=book();clock.at(DAY.atTime(8,16));
        var d=statistics.dashboard("admin",DAY,1);assertThat(d.rooms().get(0).occupiedSeats()).isZero();assertThat(d.daily().getCheckinRate()).isZero();assertThat(d.daily().expired()).isZero();
        maintenance.runOnce();assertThat(statistics.dashboard("admin",DAY,1).daily().expired()).isEqualTo(1);
        assertThat(records.selectById(id).getStatus()).isEqualTo("EXPIRED");
    }
    @Test void trendFillsSevenCalendarDaysAcrossMonthAndNoDenominatorIsNull() {
        clock.at(LocalDate.of(2030,6,2).atTime(10,0));
        raw(student,seat,LocalDate.of(2030,5,30),1,"EXPIRED",LocalDate.of(2030,5,30).atTime(8,15),null);
        var d=statistics.dashboard("admin",LocalDate.of(2030,6,3),1);
        assertThat(d.trend()).hasSize(7);assertThat(d.trend().get(0).date()).isEqualTo(LocalDate.of(2030,5,27));assertThat(d.trend().get(3).students()).isEqualTo(1);
        assertThat(d.daily().getCheckinRate()).isNull();assertThat(d.daily().reservations()).isZero();
    }
    @Test void historicalDisabledSeatsCanExceedHundredPercentAndZeroCapacityIsUndefined() {
        raw(student,seat,DAY,1,"COMPLETED",DAY.atTime(8,15),DAY.atTime(8,0));
        raw(student,secondSeat,DAY,1,"COMPLETED",DAY.atTime(8,15),DAY.atTime(8,0));
        jdbc.update("UPDATE seat SET status=0 WHERE id<>?",seat);
        assertThat(statistics.dashboard("admin",DAY,1).rooms().get(0).getReservationRate()).isEqualTo(200.0);
        jdbc.update("UPDATE seat SET status=0");assertThat(statistics.dashboard("admin",DAY,1).rooms().get(0).getReservationRate()).isNull();
    }
    @Test void statisticsPageSerializesChartsSafelyAndServesEchartsLocally() throws Exception {
        jdbc.update("UPDATE reading_room SET room_name=? WHERE id=?","</script><script>alert(1)</script>",room);
        mvc.perform(get("/admin/statistics").with(user("librarian").roles("LIBRARIAN")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("window.libraryStatistics")))
                .andExpect(content().string(not(containsString("</script><script>alert(1)</script>"))));
        mvc.perform(get("/webjars/echarts/5.6.0/dist/echarts.min.js")).andExpect(status().isOk());
    }
    interface Operation {void run() throws Exception;}
    List<Boolean> race(Operation first,Operation second) throws Exception {
        var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);var ready=new CountDownLatch(2);
        try {
            List<Future<Boolean>> jobs=new ArrayList<>();
            for(var action:List.of(first,second)) jobs.add(pool.submit(() -> {ready.countDown();if(!start.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("start timeout");
                try {action.run();return true;} catch(ReservationConflictException|MaintenanceConflictException|AccessDeniedException e) {return false;} }));
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue();start.countDown();return List.of(jobs.get(0).get(20,TimeUnit.SECONDS),jobs.get(1).get(20,TimeUnit.SECONDS));
        } finally {pool.shutdownNow();}
    }
    @Test void adminCancelRacingCheckinHasOneWinnerAndNeverCancelsUsing() throws Exception {
        Long id=book();clock.at(DAY.atTime(8,0));
        assertThat(race(() -> adminReservations.cancel("admin",id,"违规"),() -> lifecycle.checkIn("student",id))).containsExactlyInAnyOrder(true,false);
        var record=records.selectById(id);assertThat(record.getStatus()).isIn("CANCELLED","USING");
        if(record.getStatus().equals("USING")) assertThat(record.getCancelledBy()).isNull();else assertThat(record.getCheckinAt()).isNull();
    }
    @Test void adminCancelAtDeadlineIsValidButRacingLateExpiryDoesNotInventCancellation() throws Exception {
        Long id=book();clock.at(DAY.atTime(8,15));
        assertThat(adminReservations.cancel("admin",id,"截止时违规")).isEqualTo(LifecycleResult.CANCELLED);
        Long late=reservations.create("second",room,seat,DAY,1);clock.at(DAY.atTime(8,15,1));
        assertThat(race(() -> adminReservations.cancel("admin",late,"迟到"),() -> maintenance.runOnce())).containsExactlyInAnyOrder(false,true);
        var result=records.selectById(late);assertThat(result.getStatus()).isEqualTo("EXPIRED");assertThat(result.getCancelledBy()).isNull();
    }
    @Test void duplicateConcurrentAdminCancellationsKeepOneAuditedMutation() throws Exception {
        Long id=book();AtomicReference<LifecycleResult> a=new AtomicReference<>(),b=new AtomicReference<>();
        assertThat(race(() -> a.set(adminReservations.cancel("admin",id,"第一原因")),() -> b.set(adminReservations.cancel("librarian",id,"第二原因")))).containsOnly(true);
        assertThat(List.of(a.get(),b.get())).containsExactlyInAnyOrder(LifecycleResult.CANCELLED,LifecycleResult.NONE);
        var result=records.selectById(id);assertThat(result.getStatus()).isEqualTo("CANCELLED");assertThat(result.getCancelledBy()).isNotNull();
        assertThat(result.getCancelReason()).isIn("第一原因","第二原因");
    }
    @Test void disableRacingBookingCannotLeaveActiveBookingOnDisabledUser() throws Exception {
        race(() -> users.enabled("admin",student,false),this::book);
        var u=accounts.findByUsername("student");int active=jdbc.queryForObject("SELECT COUNT(*) FROM reserve_record WHERE user_id=? AND status='PENDING'",Integer.class,student);
        assertThat((u.getStatus()==0 && active==0)||(u.getStatus()==1 && active==1)).isTrue();
    }
    @Test void roleChangeRacingBookingCannotStrandStudentReservation() throws Exception {
        race(() -> users.role("admin",student,Role.LIBRARIAN),this::book);
        var u=accounts.findByUsername("student");int active=jdbc.queryForObject("SELECT COUNT(*) FROM reserve_record WHERE user_id=? AND status='PENDING'",Integer.class,student);
        assertThat((u.getRole().equals("LIBRARIAN") && active==0)||(u.getRole().equals("STUDENT") && active==1)).isTrue();
    }
    @Test void simultaneousSuperAdminMutationsPreserveAnEnabledSuperAdmin() throws Exception {
        Long other=accounts.findByUsername("librarian").getId();users.role("admin",other,Role.SUPER_ADMIN);
        assertThat(race(() -> users.role("admin",other,Role.STUDENT),() -> users.role("librarian",admin,Role.STUDENT))).containsExactlyInAnyOrder(true,false);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM `user` WHERE role='SUPER_ADMIN' AND status=1",Integer.class)).isEqualTo(1);
    }
}
