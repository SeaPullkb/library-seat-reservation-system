package com.seapullkb.library;

import com.seapullkb.library.service.AccountService;
import com.seapullkb.library.service.InitialDataService;
import com.seapullkb.library.web.RegistrationForm;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class FoundationIntegrationTest {
    private static final String PASSWORD = "Integration-test-123!";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("library_test");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired InitialDataService initialData;
    @Autowired AccountService accounts;
    @Autowired PasswordEncoder passwords;
    @Autowired Clock clock;

    @BeforeEach
    void seed() {
        clear();
        initialData.initialize(true, PASSWORD);
    }

    private void clear() {
        jdbc.update("DELETE FROM reserve_record");
        jdbc.update("DELETE FROM seat");
        jdbc.update("DELETE FROM reading_room");
        jdbc.update("DELETE FROM `user`");
    }

    private MockHttpSession login(String username) throws Exception {
        return (MockHttpSession) mvc.perform(formLogin("/login").user(username).password(PASSWORD))
                .andExpect(authenticated().withUsername(username))
                .andExpect(redirectedUrl("/"))
                .andReturn().getRequest().getSession(false);
    }

    @Test
    void fourTablesAndShanghaiBusinessClock() {
        List<String> tables = jdbc.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()", String.class);
        assertThat(tables).containsExactlyInAnyOrder("user", "reading_room", "seat", "reserve_record");
        assertThat(clock.getZone().getId()).isEqualTo("Asia/Shanghai");
    }

    @Test
    void schemaAndSeedCanBeRepeatedWithoutResettingPasswordsOrDuplicatingSeats() {
        String originalHash = accounts.findByUsername("admin").getPassword();
        new ResourceDatabasePopulator(new ClassPathResource("db/schema.sql")).execute(dataSource);
        initialData.initialize(true, "Different-password-456!");
        assertThat(accounts.findByUsername("admin").getPassword()).isEqualTo(originalHash);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM `user`", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM seat", Integer.class)).isEqualTo(20);
    }

    @Test
    void productionBootstrapCreatesOnlySuperAdminAndRequiresPassword() {
        clear();
        assertThatThrownBy(() -> initialData.initialize(false, "")).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM `user`", Integer.class)).isZero();
        initialData.initialize(false, PASSWORD);
        initialData.initialize(false, "");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM `user`", Integer.class)).isEqualTo(1);
        assertThat(accounts.findByUsername("admin").getRole()).isEqualTo("SUPER_ADMIN");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reading_room", Integer.class)).isZero();
    }

    @Test
    void uniqueSeatNumbersAreScopedToReadingRoomAndForeignKeysProtectHistory() {
        Long room = jdbc.queryForObject("SELECT id FROM reading_room LIMIT 1", Long.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO seat (room_id,seat_no) VALUES (?, 'A01')", room))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("INSERT INTO reading_room (room_name,location) VALUES ('第二阅览室','三层')");
        Long otherRoom = jdbc.queryForObject("SELECT MAX(id) FROM reading_room", Long.class);
        jdbc.update("INSERT INTO seat (room_id,seat_no) VALUES (?, 'A01')", otherRoom);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM reading_room WHERE id = ?", room))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO seat (room_id,seat_no) VALUES (-1, 'X01')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void expiredAndNewReservationsCanCoexistAndHistoricalReferencesCannotBeDeleted() {
        Long seat = jdbc.queryForObject("SELECT MIN(id) FROM seat", Long.class);
        Long user = accounts.findByUsername("student").getId();
        String insert = "INSERT INTO reserve_record (user_id,seat_id,reserve_date,time_slot,status,checkin_deadline) VALUES (?,?,'2030-01-01',1,?,'2030-01-01 08:15:00')";
        jdbc.update(insert, user, seat, "EXPIRED");
        jdbc.update(insert, user, seat, "PENDING");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reserve_record", Integer.class)).isEqualTo(2);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM seat WHERE id = ?", seat))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM `user` WHERE id = ?", user))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, user, seat, "INVALID"))
                .hasRootCauseInstanceOf(java.sql.SQLException.class)
                .rootCause().hasMessageContaining("ck_reserve_status");
    }

    @Test
    void loginRegistrationAndLocalBootstrapAssetsAreAccessible() throws Exception {
        mvc.perform(get("/login")).andExpect(status().isOk())
                .andExpect(content().string(containsString("欢迎回来")))
                .andExpect(content().string(containsString("name=\"_csrf\"")));
        mvc.perform(get("/register")).andExpect(status().isOk())
                .andExpect(content().string(containsString("注册学生账号")));
        mvc.perform(get("/webjars/bootstrap/5.3.8/css/bootstrap.min.css")).andExpect(status().isOk());
        mvc.perform(get("/css/app.css")).andExpect(status().isOk());
        mvc.perform(get("/js/app.js")).andExpect(status().isOk());
        mvc.perform(get("/student")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost/login"));
    }

    @Test
    void registrationAlwaysCreatesEnabledStudentAndHashesPassword() throws Exception {
        mvc.perform(post("/register").with(csrf()).param("username", "new_student")
                .param("password", PASSWORD).param("confirmPassword", PASSWORD)
                .param("realName", "新同学").param("className", "计算机一班").param("phone", "13912345678")
                .param("role", "SUPER_ADMIN").param("status", "0").param("id", "1"))
                .andExpect(redirectedUrl("/login"));
        var account = accounts.findByUsername("new_student");
        assertThat(account.getRole()).isEqualTo("STUDENT");
        assertThat(account.getStatus()).isEqualTo(1);
        assertThat(account.getPassword()).isNotEqualTo(PASSWORD);
        assertThat(passwords.matches(PASSWORD, account.getPassword())).isTrue();
        login("new_student");
    }

    @Test
    void duplicateUsernameIsRejectedWithoutChangingExistingAccount() throws Exception {
        String original = accounts.findByUsername("student").getPassword();
        mvc.perform(post("/register").with(csrf()).param("username", "STUDENT")
                .param("password", PASSWORD).param("confirmPassword", PASSWORD)
                .param("realName", "另一学生").param("className", "一班").param("phone", "13912345678"))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("registrationForm", "username"))
                .andExpect(content().string(containsString("该账号已被注册")));
        assertThat(accounts.findByUsername("student").getPassword()).isEqualTo(original);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM `user`", Integer.class)).isEqualTo(3);
    }

    @ParameterizedTest
    @CsvSource({"a,Valid-password1!,Valid-password1!,13912345678", "validuser,short,short,13912345678",
            "validuser,Valid-password1!,Different-pass1!,13912345678", "validuser,Valid-password1!,Valid-password1!,12345"})
    void invalidRegistrationDoesNotCreateAnAccount(String username, String password, String confirmation, String phone) throws Exception {
        mvc.perform(post("/register").with(csrf()).param("username", username).param("password", password)
                .param("confirmPassword", confirmation).param("realName", "同学").param("className", "一班").param("phone", phone))
                .andExpect(status().isOk()).andExpect(model().hasErrors());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM `user`", Integer.class)).isEqualTo(3);
    }

    @Test
    void unicodePasswordBeyondBcryptLimitReturnsValidationError() throws Exception {
        String longPassword = "学".repeat(25);
        mvc.perform(post("/register").with(csrf()).param("username", "validuser").param("password", longPassword)
                .param("confirmPassword", longPassword).param("realName", "同学").param("className", "一班").param("phone", "13912345678"))
                .andExpect(status().isOk()).andExpect(model().hasErrors());
    }

    @Test
    void missingCsrfIsRejectedBeforeRegistrationOrLogin() throws Exception {
        mvc.perform(post("/register").param("username", "malicious")).andExpect(status().isForbidden());
        mvc.perform(post("/login").param("username", "student").param("password", PASSWORD)).andExpect(status().isForbidden());
        assertThat(accounts.findByUsername("malicious")).isNull();
    }

    @ParameterizedTest
    @CsvSource({"student,/student,学生空间", "librarian,/admin,图书馆管理员", "admin,/admin,超级管理员"})
    void realLoginRoutesAllThreeRolesToTheirRenderedHome(String username, String home, String expected) throws Exception {
        MockHttpSession session = login(username);
        mvc.perform(get("/").session(session)).andExpect(redirectedUrl(home));
        mvc.perform(get(home).session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString(expected)));
    }

    @Test
    void studentAndLibrarianCannotEnterSuperAdminPages() throws Exception {
        MockHttpSession student = login("student");
        mvc.perform(get("/admin").session(student)).andExpect(status().isForbidden());
        mvc.perform(get("/admin/users").session(student)).andExpect(status().isForbidden());
        mvc.perform(get("/admin/users").session(login("librarian"))).andExpect(status().isForbidden());
        mvc.perform(get("/admin/users").session(login("admin"))).andExpect(status().isOk())
                .andExpect(content().string(containsString("用户管理")));
    }

    @Test
    void disabledUserAndWrongPasswordCannotLogin() throws Exception {
        jdbc.update("UPDATE `user` SET status = 0 WHERE username = 'student'");
        mvc.perform(formLogin().user("student").password(PASSWORD)).andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?error"));
        mvc.perform(formLogin().user("librarian").password("wrong-password")).andExpect(unauthenticated());
    }

    @Test
    void logoutInvalidatesSessionAndNeedsCsrf() throws Exception {
        MockHttpSession session = login("student");
        mvc.perform(post("/logout").session(session)).andExpect(status().isForbidden());
        mvc.perform(get("/student").session(session)).andExpect(status().isOk());
        mvc.perform(post("/logout").with(csrf()).session(session)).andExpect(redirectedUrl("/login?logout"))
                .andExpect(unauthenticated());
        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/student")).andExpect(status().is3xxRedirection());
    }

    @Test
    void userControlledDisplayNamesAreHtmlEscaped() throws Exception {
        jdbc.update("UPDATE `user` SET real_name = ? WHERE username = 'student'", "<script>alert(1)</script>");
        mvc.perform(get("/student").session(login("student"))).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("<script>alert(1)</script>"))))
                .andExpect(content().string(containsString("&lt;script&gt;alert(1)&lt;/script&gt;")));
    }

    @Test
    void concurrentRegistrationsOfSameUsernameHaveExactlyOneWinner() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        var form = new RegistrationForm("race_student", PASSWORD, PASSWORD, "并发同学", "一班", "13912345678");
        try {
            var tasks = new java.util.ArrayList<java.util.concurrent.Future<Boolean>>();
            for (int i = 0; i < 2; i++) {
                tasks.add(executor.submit(() -> {
                    if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("未开始并发测试");
                    try { accounts.registerStudent(form); return true; }
                    catch (AccountService.UsernameTakenException ex) { return false; }
                }));
            }
            start.countDown();
            assertThat(List.of(tasks.get(0).get(20, TimeUnit.SECONDS), tasks.get(1).get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM `user` WHERE username = 'race_student'", Integer.class)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }
}
