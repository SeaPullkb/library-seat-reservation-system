package com.seapullkb.library;

import com.seapullkb.library.entity.ReservationStatus;
import com.seapullkb.library.mapper.ReservationMapper;
import com.seapullkb.library.service.*;
import com.seapullkb.library.web.RegistrationForm;
import java.time.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
@Import(LifecycleIntegrationTest.TimeConfiguration.class)
class LifecycleIntegrationTest {
    private static final LocalDate DAY = LocalDate.of(2030, 5, 8);
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0").withDatabaseName("lifecycle_test");
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", MYSQL::getJdbcUrl);
        properties.add("spring.datasource.username", MYSQL::getUsername);
        properties.add("spring.datasource.password", MYSQL::getPassword);
    }
    static class MutableClock extends Clock {
        private final AtomicReference<Instant> value = new AtomicReference<>();
        void at(LocalDateTime time) { value.set(time.atZone(getZone()).toInstant()); }
        @Override public ZoneId getZone() { return ZoneId.of("Asia/Shanghai"); }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant(), zone); }
        @Override public Instant instant() { return value.get(); }
    }
    @TestConfiguration
    static class TimeConfiguration {
        @Bean @Primary
        MutableClock lifecycleClock() { var clock = new MutableClock(); clock.at(DAY.atTime(7, 30)); return clock; }
    }

    @Autowired MutableClock clock;
    @Autowired ReservationService reservations;
    @Autowired ReservationLifecycleService lifecycle;
    @Autowired ReservationMaintenance maintenance;
    @Autowired ReservationMapper records;
    @Autowired SpaceService spaces;
    @Autowired AccountService accounts;
    @Autowired InitialDataService initialData;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    Long room;
    Long seat;
    Long secondSeat;
    Long student;

    @BeforeEach
    void seed() {
        clock.at(DAY.atTime(7, 30));
        jdbc.update("DELETE FROM reserve_record"); jdbc.update("DELETE FROM seat");
        jdbc.update("DELETE FROM reading_room"); jdbc.update("DELETE FROM `user`");
        initialData.initialize(true, "Lifecycle-test-123!");
        accounts.registerStudent(new RegistrationForm("second", "Lifecycle-test-123!", "Lifecycle-test-123!", "第二位同学", "二班", "13912345678"));
        room = jdbc.queryForObject("SELECT MIN(id) FROM reading_room", Long.class);
        seat = jdbc.queryForObject("SELECT MIN(id) FROM seat", Long.class);
        secondSeat = jdbc.queryForObject("SELECT id FROM seat WHERE seat_no='A02'", Long.class);
        student = accounts.findByUsername("student").getId();
    }
    private Long book(String user, Long seatId) { return reservations.create(user, room, seatId, DAY, 1); }
    private String storedStatus(Long id) { return records.selectById(id).getStatus(); }
    private boolean tryAction(Runnable action) {
        try { action.run(); return true; } catch (ReservationConflictException ex) { return false; }
    }
    private <T> List<T> race(Callable<T> first, Callable<T> second) throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2); var start = new CountDownLatch(1);
        try {
            var a = executor.submit(() -> { ready.countDown(); if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException(); return first.call(); });
            var b = executor.submit(() -> { ready.countDown(); if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException(); return second.call(); });
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); start.countDown();
            return List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
        } finally { executor.shutdownNow(); }
    }

    @Test
    void normalCheckinOpensExactlyFifteenMinutesBeforeStartAndIsIdempotent() {
        Long id = book("student", seat);
        clock.at(DAY.atTime(7, 44, 59));
        assertThatThrownBy(() -> lifecycle.checkIn("student", id)).isInstanceOf(ReservationConflictException.class);
        assertThat(storedStatus(id)).isEqualTo("PENDING");
        clock.at(DAY.atTime(7, 45));
        assertThat(lifecycle.checkIn("student", id)).isEqualTo(LifecycleResult.CHECKED_IN);
        assertThat(records.selectById(id).getCheckinAt()).isEqualTo(DAY.atTime(7, 45));
        clock.at(DAY.atTime(8, 0));
        assertThat(lifecycle.checkIn("student", id)).isEqualTo(LifecycleResult.ALREADY_CHECKED_IN);
        assertThat(records.selectById(id).getCheckinAt()).isEqualTo(DAY.atTime(7, 45));
    }

    @Test
    void checkinAtDeadlineSucceedsButOneSecondLaterReleasesAndRejectsEvenWithoutSweep() {
        Long first = book("student", seat); Long second = book("second", secondSeat);
        clock.at(DAY.atTime(8, 15));
        assertThat(lifecycle.checkIn("student", first)).isEqualTo(LifecycleResult.CHECKED_IN);
        clock.at(DAY.atTime(8, 15, 1));
        assertThatThrownBy(() -> lifecycle.checkIn("second", second)).isInstanceOf(ReservationConflictException.class);
        assertThat(storedStatus(second)).isEqualTo("EXPIRED");
        assertThat(records.selectById(second).getReleasedAt()).isEqualTo(DAY.atTime(8, 15, 1));
        assertThat(records.selectById(second).getCheckinAt()).isNull();
        assertThat(storedStatus(first)).isEqualTo("USING");
    }

    @Test
    void futureReservationCannotCheckinTodayOrCheckoutBeforeCheckin() {
        Long id = reservations.create("student", room, seat, DAY.plusDays(1), 1);
        clock.at(DAY.atTime(8, 0));
        assertThatThrownBy(() -> lifecycle.checkIn("student", id)).isInstanceOf(ReservationConflictException.class);
        assertThatThrownBy(() -> lifecycle.checkOut("student", id)).isInstanceOf(ReservationConflictException.class);
        assertThat(storedStatus(id)).isEqualTo("PENDING");
    }

    @Test
    void earlyCheckoutLocksBothSeatAndStudentForWholeSlotAndDoesNotRewriteOnRepeat() {
        Long id = book("student", seat);
        clock.at(DAY.atTime(7, 45)); lifecycle.checkIn("student", id);
        clock.at(DAY.atTime(10, 0));
        assertThat(lifecycle.checkOut("student", id)).isEqualTo(LifecycleResult.CHECKED_OUT);
        assertThat(records.selectById(id).getCheckoutAt()).isEqualTo(DAY.atTime(10, 0));
        clock.at(DAY.atTime(10, 1));
        assertThat(lifecycle.checkOut("student", id)).isEqualTo(LifecycleResult.ALREADY_COMPLETED);
        assertThat(records.selectById(id).getCheckoutAt()).isEqualTo(DAY.atTime(10, 0));
        assertThatThrownBy(() -> book("second", seat)).isInstanceOf(ReservationConflictException.class);
        assertThatThrownBy(() -> book("student", secondSeat)).isInstanceOf(ReservationConflictException.class);
        assertThat(spaces.studentGrid(room, DAY, 1, accounts.findByUsername("second").getId()).get(0).availability()).isEqualTo("OCCUPIED");
        assertThatThrownBy(() -> lifecycle.checkIn("student", id)).isInstanceOf(ReservationConflictException.class);
        assertThatThrownBy(() -> reservations.cancel("student", id)).isInstanceOf(ReservationConflictException.class);
    }

    @Test
    void lateCheckoutWithoutSchedulerCompletesAtSlotEndInsteadOfRequestTime() {
        Long id = book("student", seat);
        clock.at(DAY.atTime(8, 0)); lifecycle.checkIn("student", id);
        clock.at(DAY.atTime(13, 0));
        assertThat(lifecycle.checkOut("student", id)).isEqualTo(LifecycleResult.AUTO_COMPLETED);
        assertThat(storedStatus(id)).isEqualTo("COMPLETED");
        assertThat(records.selectById(id).getCheckoutAt()).isEqualTo(DAY.atTime(12, 0));
        clock.at(DAY.atTime(14, 0));
        assertThat(lifecycle.checkOut("student", id)).isEqualTo(LifecycleResult.ALREADY_COMPLETED);
        assertThat(records.selectById(id).getCheckoutAt()).isEqualTo(DAY.atTime(12, 0));
    }

    @Test
    void checkinRequestAfterEndAlsoCommitsAutomaticCompletion() {
        Long id = book("student", seat); clock.at(DAY.atTime(8, 0)); lifecycle.checkIn("student", id);
        clock.at(DAY.atTime(12, 0));
        assertThatThrownBy(() -> lifecycle.checkIn("student", id)).isInstanceOf(ReservationConflictException.class);
        assertThat(storedStatus(id)).isEqualTo("COMPLETED");
        assertThat(records.selectById(id).getCheckoutAt()).isEqualTo(DAY.atTime(12, 0));
    }

    @Test
    void sweepDoesNotExpireAtDeadlineButExpiresImmediatelyAfterAndIsRepeatable() {
        Long id = book("student", seat);
        clock.at(DAY.atTime(8, 15));
        assertThat(maintenance.runOnce().scanned()).isZero();
        clock.at(DAY.atTime(8, 15, 1));
        var result = maintenance.runOnce();
        assertThat(result.expired()).isEqualTo(1); assertThat(result.failed()).isZero();
        assertThat(storedStatus(id)).isEqualTo("EXPIRED");
        var time = records.selectById(id).getReleasedAt();
        clock.at(DAY.atTime(8, 20)); assertThat(maintenance.runOnce().scanned()).isZero();
        assertThat(records.selectById(id).getReleasedAt()).isEqualTo(time);
    }

    @Test
    void overdueOriginalCanBeRebookedCheckedInAndCheckedOutWithoutExtendingEnd() {
        Long old = book("student", seat);
        clock.at(DAY.atTime(8, 16)); assertThat(maintenance.runOnce().expired()).isEqualTo(1);
        clock.at(DAY.atTime(8, 20)); Long replacement = book("second", seat);
        assertThat(storedStatus(old)).isEqualTo("EXPIRED");
        assertThat(records.selectById(replacement).getCheckinDeadline()).isEqualTo(DAY.atTime(8, 30));
        clock.at(DAY.atTime(8, 26)); lifecycle.checkIn("second", replacement);
        assertThat(storedStatus(replacement)).isEqualTo("USING");
        clock.at(DAY.atTime(10, 0)); lifecycle.checkOut("second", replacement);
        assertThat(storedStatus(replacement)).isEqualTo("COMPLETED");
        assertThatThrownBy(() -> book("student", seat)).isInstanceOf(ReservationConflictException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reserve_record", Integer.class)).isEqualTo(2);
    }

    @Test
    void lateRebookingGetsExactlyTenMinutesAndMayExpireAgain() {
        clock.at(DAY.atTime(8, 20)); Long id = book("student", seat);
        assertThat(records.selectById(id).getCheckinDeadline()).isEqualTo(DAY.atTime(8, 30));
        clock.at(DAY.atTime(8, 30)); assertThat(maintenance.runOnce().expired()).isZero();
        clock.at(DAY.atTime(8, 30, 1)); assertThat(maintenance.runOnce().expired()).isEqualTo(1);
        Long replacement = book("second", seat);
        assertThat(records.selectById(replacement).getCheckinDeadline()).isEqualTo(DAY.atTime(8, 40, 1));
        clock.at(DAY.atTime(8, 40, 1));
        assertThat(lifecycle.checkIn("second", replacement)).isEqualTo(LifecycleResult.CHECKED_IN);
        clock.at(DAY.atTime(12, 0));
        assertThat(maintenance.runOnce().completed()).isEqualTo(1);
        assertThat(records.selectById(replacement).getCheckoutAt()).isEqualTo(DAY.atTime(12, 0));
    }

    @Test
    void sweepHandlesMoreThanOnePageAndBothKindsOfDueRecords() {
        for (int i = 0; i < 205; i++) {
            LocalDate date = DAY.minusDays(i + 1);
            jdbc.update("INSERT INTO reserve_record(user_id,seat_id,reserve_date,time_slot,status,checkin_deadline) VALUES (?,?,?,1,'PENDING',?)",
                    student, seat, date, date.atTime(8, 15));
        }
        Long using = book("student", secondSeat); clock.at(DAY.atTime(8, 0)); lifecycle.checkIn("student", using);
        clock.at(DAY.atTime(12, 0)); var result = maintenance.runOnce();
        assertThat(result.scanned()).isEqualTo(206); assertThat(result.expired()).isEqualTo(205);
        assertThat(result.completed()).isEqualTo(1); assertThat(result.failed()).isZero();
        assertThat(maintenance.runOnce().scanned()).isZero();
    }

    @Test
    void staleExpirationAndCompletionWritesCannotOverwriteCurrentStates() {
        Long id = book("student", seat); clock.at(DAY.atTime(8, 0)); lifecycle.checkIn("student", id);
        clock.at(DAY.atTime(8, 16));
        assertThat(records.expireOne(id, DAY.atTime(8, 16))).isZero();
        assertThat(lifecycle.processDue(id)).isEqualTo(LifecycleResult.NONE);
        assertThat(storedStatus(id)).isEqualTo("USING");
        assertThat(records.checkIn(id, student, DAY.atTime(8, 0))).isZero();
        lifecycle.checkOut("student", id);
        assertThat(records.complete(id, DAY.atTime(12, 0), DAY.atTime(12, 0))).isZero();
        assertThat(records.selectById(id).getCheckoutAt()).isEqualTo(DAY.atTime(8, 16));
    }

    @ParameterizedTest
    @ValueSource(strings = {"CANCELLED", "EXPIRED", "COMPLETED"})
    void terminalRecordsCannotReturnToUsing(String state) {
        Long id = book("student", seat);
        jdbc.update("UPDATE reserve_record SET status=? WHERE id=?", state, id);
        clock.at(DAY.atTime(8, 0));
        assertThatThrownBy(() -> lifecycle.checkIn("student", id)).isInstanceOf(ReservationConflictException.class);
        assertThat(storedStatus(id)).isEqualTo(state);
        assertThat(records.selectById(id).getCheckinAt()).isNull();
    }

    @Test
    void onlyOwnerCanUseLifecycleEndpointsAndTimestampsComeFromServer() throws Exception {
        Long id = book("student", seat); clock.at(DAY.atTime(8, 0));
        for (String action : List.of("checkin", "checkout")) {
            String path = "/student/reservations/" + id + "/" + action;
            mvc.perform(post(path).with(user("second").roles("STUDENT")).with(csrf())).andExpect(status().isNotFound());
            mvc.perform(post(path).with(user("student").roles("STUDENT"))).andExpect(status().isForbidden());
            mvc.perform(post(path).with(user("admin").roles("SUPER_ADMIN")).with(csrf())).andExpect(status().isForbidden());
        }
        mvc.perform(post("/student/reservations/" + id + "/checkin").with(user("student").roles("STUDENT")).with(csrf())
                .param("checkinAt", "2099-01-01T00:00:00").param("status", "COMPLETED").param("userId", "9999"))
                .andExpect(redirectedUrl("/student/reservations")).andExpect(flash().attribute("notice", containsString("签到成功")));
        assertThat(records.selectById(id).getCheckinAt()).isEqualTo(DAY.atTime(8, 0));
        clock.at(DAY.atTime(9, 0));
        mvc.perform(post("/student/reservations/" + id + "/checkout").with(user("student").roles("STUDENT")).with(csrf())
                .param("checkoutAt", "2099-01-01T00:00:00"))
                .andExpect(flash().attribute("notice", containsString("签退成功")));
        assertThat(records.selectById(id).getCheckoutAt()).isEqualTo(DAY.atTime(9, 0));
    }

    @Test
    void disabledUsersCannotCheckinButAutomaticCleanupStillWorks() throws Exception {
        Long id = book("student", seat); jdbc.update("UPDATE `user` SET status=0 WHERE id=?", student);
        clock.at(DAY.atTime(8, 0));
        mvc.perform(post("/student/reservations/" + id + "/checkin").with(user("student").roles("STUDENT")).with(csrf())).andExpect(status().isForbidden());
        clock.at(DAY.atTime(8, 16));
        assertThat(maintenance.runOnce().expired()).isEqualTo(1); assertThat(storedStatus(id)).isEqualTo("EXPIRED");
    }

    @Test
    void forcedDisabledResourceCannotBeCheckedInAndDoesNotBlockExpiration() {
        Long id = book("student", seat);
        jdbc.update("UPDATE seat SET status=0 WHERE id=?", seat);
        clock.at(DAY.atTime(8, 0));
        assertThatThrownBy(() -> lifecycle.checkIn("student", id)).isInstanceOf(ReservationConflictException.class);
        clock.at(DAY.atTime(8, 16));
        assertThat(maintenance.runOnce().expired()).isEqualTo(1);
    }

    @Test
    void ownPageShowsEligibleActionsAndActualTimesAndDoesNotMutateOnGet() throws Exception {
        Long id = book("student", seat);
        mvc.perform(get("/student/reservations").with(user("student").roles("STUDENT")))
                .andExpect(content().string(containsString("起可签到")))
                .andExpect(content().string(not(containsString("aria-label=\"签到预约"))));
        clock.at(DAY.atTime(7, 45));
        mvc.perform(get("/student/reservations").with(user("student").roles("STUDENT")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("aria-label=\"签到预约")));
        lifecycle.checkIn("student", id);
        mvc.perform(get("/student/reservations").with(user("student").roles("STUDENT")))
                .andExpect(content().string(containsString("aria-label=\"签退预约")))
                .andExpect(content().string(containsString("05-08 07:45:00")));
        clock.at(DAY.atTime(12, 0));
        assertThat(reservations.mine("student", ReservationStatus.COMPLETED, 0).total()).isEqualTo(1);
        mvc.perform(get("/student/reservations?status=COMPLETED").with(user("student").roles("STUDENT")))
                .andExpect(content().string(not(containsString("aria-label=\"签退预约"))));
        assertThat(storedStatus(id)).isEqualTo("USING");
    }

    @Test
    void duplicateConcurrentCheckinsAndCheckoutsEachChangeStateOnlyOnce() throws Exception {
        Long id = book("student", seat); clock.at(DAY.atTime(8, 0));
        assertThat(race(() -> lifecycle.checkIn("student", id), () -> lifecycle.checkIn("student", id)))
                .containsExactlyInAnyOrder(LifecycleResult.CHECKED_IN, LifecycleResult.ALREADY_CHECKED_IN);
        clock.at(DAY.atTime(9, 0));
        assertThat(race(() -> lifecycle.checkOut("student", id), () -> lifecycle.checkOut("student", id)))
                .containsExactlyInAnyOrder(LifecycleResult.CHECKED_OUT, LifecycleResult.ALREADY_COMPLETED);
        assertThat(records.selectById(id).getCheckinAt()).isEqualTo(DAY.atTime(8, 0));
        assertThat(records.selectById(id).getCheckoutAt()).isEqualTo(DAY.atTime(9, 0));
    }

    @Test
    void checkinRacingCancellationHasExactlyOneLegalWinner() throws Exception {
        Long id = book("student", seat); clock.at(DAY.atTime(7, 45));
        assertThat(race(() -> tryAction(() -> lifecycle.checkIn("student", id)),
                () -> tryAction(() -> reservations.cancel("student", id)))).containsExactlyInAnyOrder(true, false);
        assertThat(storedStatus(id)).isIn("USING", "CANCELLED");
        var row = records.selectById(id);
        assertThat(row.getCheckinAt() != null).isNotEqualTo(row.getCancelledAt() != null);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void checkinRacingExpirationHonorsDeadlineAndNeverOverwritesSuccessfulCheckin(int secondsLate) throws Exception {
        Long id = book("student", seat); clock.at(DAY.atTime(8, 15).plusSeconds(secondsLate));
        var results = race(() -> tryAction(() -> lifecycle.checkIn("student", id)), () -> lifecycle.processDue(id) == LifecycleResult.EXPIRED);
        if (secondsLate == 0) {
            assertThat(results).containsExactly(true, false); assertThat(storedStatus(id)).isEqualTo("USING");
            assertThat(records.selectById(id).getReleasedAt()).isNull();
        } else {
            assertThat(results.get(0)).isFalse(); assertThat(storedStatus(id)).isEqualTo("EXPIRED");
            assertThat(records.selectById(id).getCheckinAt()).isNull(); assertThat(records.selectById(id).getReleasedAt()).isNotNull();
        }
    }

    @Test
    void checkoutRacingAutomaticCompletionPreservesExactEndAndSingleCompletion() throws Exception {
        Long id = book("student", seat); clock.at(DAY.atTime(8, 0)); lifecycle.checkIn("student", id);
        clock.at(DAY.atTime(12, 0));
        var results = race(() -> lifecycle.checkOut("student", id), () -> lifecycle.processDue(id));
        assertThat(results).contains(LifecycleResult.AUTO_COMPLETED);
        assertThat(storedStatus(id)).isEqualTo("COMPLETED");
        assertThat(records.selectById(id).getCheckoutAt()).isEqualTo(DAY.atTime(12, 0));
    }
}
