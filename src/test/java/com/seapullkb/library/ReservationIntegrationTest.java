package com.seapullkb.library;

import com.seapullkb.library.entity.ReservationStatus;
import com.seapullkb.library.service.*;
import com.seapullkb.library.web.RegistrationForm;
import com.seapullkb.library.web.RoomForm;
import com.seapullkb.library.web.SeatForm;
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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
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
@Import(ReservationIntegrationTest.TimeConfiguration.class)
class ReservationIntegrationTest {
    private static final LocalDate TODAY = LocalDate.of(2030, 5, 8);
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0").withDatabaseName("reservation_test");
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", MYSQL::getJdbcUrl);
        properties.add("spring.datasource.username", MYSQL::getUsername);
        properties.add("spring.datasource.password", MYSQL::getPassword);
    }

    static class MutableClock extends Clock {
        private final AtomicReference<Instant> value = new AtomicReference<>();
        void set(LocalDateTime time) { value.set(time.atZone(getZone()).toInstant()); }
        @Override public ZoneId getZone() { return ZoneId.of("Asia/Shanghai"); }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant(), zone); }
        @Override public Instant instant() { return value.get(); }
    }
    @TestConfiguration
    static class TimeConfiguration {
        @Bean @Primary
        MutableClock testClock() {
            var result = new MutableClock(); result.set(TODAY.atTime(7, 30)); return result;
        }
    }

    @Autowired ReservationService reservations;
    @Autowired SpaceService spaces;
    @Autowired AccountService accounts;
    @Autowired InitialDataService initialData;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired MutableClock clock;
    Long roomId;
    Long seatId;
    Long secondSeat;
    Long studentId;
    Long secondStudentId;

    @BeforeEach
    void seed() {
        clock.set(TODAY.atTime(7, 30));
        jdbc.update("DELETE FROM reserve_record"); jdbc.update("DELETE FROM seat");
        jdbc.update("DELETE FROM reading_room"); jdbc.update("DELETE FROM `user`");
        initialData.initialize(true, "Integration-test-123!");
        accounts.registerStudent(new RegistrationForm("second", "Integration-test-123!", "Integration-test-123!", "第二位学生", "二班", "13912345678"));
        roomId = jdbc.queryForObject("SELECT MIN(id) FROM reading_room", Long.class);
        seatId = jdbc.queryForObject("SELECT MIN(id) FROM seat", Long.class);
        secondSeat = jdbc.queryForObject("SELECT id FROM seat WHERE seat_no = 'A02'", Long.class);
        studentId = accounts.findByUsername("student").getId();
        secondStudentId = accounts.findByUsername("second").getId();
    }

    private Long book(String username, Long seat, LocalDate date, int slot) {
        return reservations.create(username, roomId, seat, date, slot);
    }
    private Long insert(Long user, Long seat, LocalDate date, int slot, String status, LocalDateTime deadline) {
        jdbc.update("INSERT INTO reserve_record(user_id,seat_id,reserve_date,time_slot,status,checkin_deadline) VALUES (?,?,?,?,?,?)",
                user, seat, date, slot, status, deadline);
        return jdbc.queryForObject("SELECT MAX(id) FROM reserve_record", Long.class);
    }
    private long activeCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM reserve_record WHERE status IN ('PENDING','USING','COMPLETED')", Long.class);
    }
    private boolean attempt(Runnable work) {
        try { work.run(); return true; }
        catch (ReservationConflictException | MaintenanceConflictException | ResourceNotFoundException ex) { return false; }
    }
    private List<Boolean> race(Callable<Boolean> left, Callable<Boolean> right) throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try {
            var a = pool.submit(() -> { ready.countDown(); if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException(); return left.call(); });
            var b = pool.submit(() -> { ready.countDown(); if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException(); return right.call(); });
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); start.countDown();
            return List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
        } catch (ExecutionException ex) {
            String details = MYSQL.execInContainer("sh", "-c", "MYSQL_PWD=\"$MYSQL_ROOT_PASSWORD\" mysql -uroot -e 'SHOW ENGINE INNODB STATUS\\G'").getStdout();
            boolean deadlock = false;
            for (String line : details.split("\\R")) {
                if (line.contains("LATEST DETECTED DEADLOCK")) deadlock = true;
                if (deadlock && line.equals("TRANSACTIONS")) break;
                if (deadlock && (line.startsWith("***") || line.contains("RECORD LOCKS") || line.startsWith("TRANSACTION ")
                        || line.startsWith("UPDATE reserve_record"))) System.out.println(line);
            }
            throw ex;
        } finally { pool.shutdownNow(); }
    }

    @Test
    void bookingUsesLoggedInIdentityAndIgnoresClientStatusAndDeadline() throws Exception {
        mvc.perform(post("/student/rooms/" + roomId + "/seats/" + seatId + "/reservations")
                .with(user("student").roles("STUDENT")).with(csrf())
                .param("date", TODAY.toString()).param("slot", "1").param("userId", secondStudentId.toString())
                .param("status", "COMPLETED").param("checkinDeadline", "2099-01-01T23:59:59"))
                .andExpect(redirectedUrl("/student/reservations"));
        assertThat(jdbc.queryForObject("SELECT user_id FROM reserve_record", Long.class)).isEqualTo(studentId);
        assertThat(jdbc.queryForObject("SELECT status FROM reserve_record", String.class)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT checkin_deadline FROM reserve_record", LocalDateTime.class)).isEqualTo(TODAY.atTime(8, 15));
    }

    @Test
    void sevenDaysAndIndependentSlotsCanAllBeBooked() {
        for (int i = 0; i < 7; i++) book("student", seatId, TODAY.plusDays(i), 1);
        book("student", seatId, TODAY, 2); book("student", seatId, TODAY, 3);
        assertThat(activeCount()).isEqualTo(9);
        assertThatThrownBy(() -> book("second", secondSeat, TODAY.minusDays(1), 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> book("second", secondSeat, TODAY.plusDays(7), 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> book("second", secondSeat, TODAY, 4)).isInstanceOf(IllegalArgumentException.class);
        assertThat(activeCount()).isEqualTo(9);
    }

    @Test
    void boundaryThirtyMinutesBeforeEndAllowsBookingAndOneSecondLaterBlocksIt() {
        clock.set(TODAY.atTime(11, 30)); book("student", seatId, TODAY, 1);
        assertThat(jdbc.queryForObject("SELECT checkin_deadline FROM reserve_record", LocalDateTime.class)).isEqualTo(TODAY.atTime(11, 40));
        clock.set(TODAY.atTime(11, 30, 1));
        assertThatThrownBy(() -> book("second", secondSeat, TODAY, 1)).isInstanceOf(ReservationConflictException.class);
        clock.set(TODAY.atTime(12, 0));
        assertThatThrownBy(() -> book("second", secondSeat, TODAY, 1)).isInstanceOf(ReservationConflictException.class);
        assertThat(activeCount()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "USING", "COMPLETED"})
    void effectiveReservationsAndCompletedUsageBlockBothSeatAndStudent(String status) {
        insert(studentId, seatId, TODAY, 1, status, TODAY.atTime(8, 15));
        assertThatThrownBy(() -> book("second", seatId, TODAY, 1)).isInstanceOf(ReservationConflictException.class);
        assertThatThrownBy(() -> book("student", secondSeat, TODAY, 1)).isInstanceOf(ReservationConflictException.class);
        assertThat(activeCount()).isEqualTo(1);
    }

    @Test
    void cancelledAndExpiredHistoryDoNotBlockNewBooking() {
        insert(studentId, seatId, TODAY, 1, "CANCELLED", TODAY.atTime(8, 15));
        insert(studentId, seatId, TODAY, 1, "EXPIRED", TODAY.atTime(8, 15));
        book("student", seatId, TODAY, 1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reserve_record", Long.class)).isEqualTo(3);
        assertThat(activeCount()).isEqualTo(1);
    }

    @Test
    void stalePendingIsReleasedInSuccessfulBookingTransactionAndNewDeadlineIsTenMinutes() {
        Long old = insert(studentId, seatId, TODAY, 1, "PENDING", TODAY.atTime(8, 15));
        clock.set(TODAY.atTime(8, 15));
        assertThatThrownBy(() -> book("second", seatId, TODAY, 1)).isInstanceOf(ReservationConflictException.class);
        clock.set(TODAY.atTime(8, 20));
        Long replacement = book("second", seatId, TODAY, 1);
        assertThat(jdbc.queryForObject("SELECT status FROM reserve_record WHERE id = ?", String.class, old)).isEqualTo("EXPIRED");
        assertThat(jdbc.queryForObject("SELECT released_at FROM reserve_record WHERE id = ?", LocalDateTime.class, old)).isEqualTo(TODAY.atTime(8, 20));
        assertThat(jdbc.queryForObject("SELECT checkin_deadline FROM reserve_record WHERE id = ?", LocalDateTime.class, replacement)).isEqualTo(TODAY.atTime(8, 30));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reserve_record", Long.class)).isEqualTo(2);
    }

    @Test
    void studentWithExpiredBookingMayBookDifferentRoomWithoutLockingOldSeat() {
        Long old = insert(studentId, seatId, TODAY, 1, "PENDING", TODAY.atTime(8, 15));
        clock.set(TODAY.atTime(8, 20));
        Long otherRoom = spaces.saveRoom(null, new RoomForm("第二阅览室", "三层", ""));
        Long otherSeat = spaces.saveSeat(otherRoom, null, new SeatForm("B01"));
        reservations.create("student", otherRoom, otherSeat, TODAY, 1);
        assertThat(reservations.mine("student", ReservationStatus.PENDING, 0).total()).isEqualTo(1);
        assertThat(reservations.mine("student", ReservationStatus.EXPIRED, 0).total()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM reserve_record WHERE id = ?", String.class, old)).isEqualTo("PENDING");
    }

    @Test
    void disabledRoomSeatOrAccountAndChangedRoleCannotCreateBooking() {
        spaces.setSeatEnabled(roomId, seatId, false);
        assertThatThrownBy(() -> book("student", seatId, TODAY, 1)).isInstanceOf(ReservationConflictException.class);
        spaces.setRoomEnabled(roomId, false);
        assertThatThrownBy(() -> book("student", secondSeat, TODAY, 1)).isInstanceOf(ReservationConflictException.class);
        spaces.setRoomEnabled(roomId, true);
        jdbc.update("UPDATE `user` SET status = 0 WHERE id = ?", studentId);
        assertThatThrownBy(() -> book("student", secondSeat, TODAY, 1)).isInstanceOf(AccessDeniedException.class);
        jdbc.update("UPDATE `user` SET status = 1, role = 'LIBRARIAN' WHERE id = ?", studentId);
        assertThatThrownBy(() -> book("student", secondSeat, TODAY, 1)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> book("admin", secondSeat, TODAY, 1)).isInstanceOf(AccessDeniedException.class);
        assertThat(activeCount()).isZero();
    }

    @Test
    void cancellationBeforeStartKeepsAuditFieldsAndRepeatedCancellationIsIdempotent() {
        Long id = book("student", seatId, TODAY, 1);
        clock.set(TODAY.atTime(7, 59, 59));
        assertThat(reservations.cancel("student", id)).isTrue();
        assertThat(jdbc.queryForObject("SELECT cancelled_by FROM reserve_record WHERE id = ?", Long.class, id)).isEqualTo(studentId);
        assertThat(jdbc.queryForObject("SELECT cancel_reason FROM reserve_record WHERE id = ?", String.class, id)).isEqualTo("学生主动取消");
        LocalDateTime cancelled = jdbc.queryForObject("SELECT cancelled_at FROM reserve_record WHERE id = ?", LocalDateTime.class, id);
        clock.set(TODAY.atTime(8, 1));
        assertThat(reservations.cancel("student", id)).isFalse();
        assertThat(jdbc.queryForObject("SELECT cancelled_at FROM reserve_record WHERE id = ?", LocalDateTime.class, id)).isEqualTo(cancelled);
        book("second", seatId, TODAY, 1);
        assertThat(activeCount()).isEqualTo(1);
    }

    @Test
    void cannotCancelAtStartOrAfterStartOrAfterCheckin() {
        Long id = book("student", seatId, TODAY, 1);
        clock.set(TODAY.atTime(8, 0));
        assertThatThrownBy(() -> reservations.cancel("student", id)).isInstanceOf(ReservationConflictException.class);
        clock.set(TODAY.atTime(7, 45));
        jdbc.update("UPDATE reserve_record SET status = 'USING', checkin_at = ? WHERE id = ?", TODAY.atTime(7, 45), id);
        assertThatThrownBy(() -> reservations.cancel("student", id)).isInstanceOf(ReservationConflictException.class);
        jdbc.update("UPDATE reserve_record SET status = 'COMPLETED' WHERE id = ?", id);
        assertThatThrownBy(() -> reservations.cancel("student", id)).isInstanceOf(ReservationConflictException.class);
    }

    @Test
    void foreignReservationCannotBeCancelledOrIncludedBySpoofedUserId() throws Exception {
        Long id = book("student", seatId, TODAY, 1);
        mvc.perform(post("/student/reservations/" + id + "/cancel").with(user("second").roles("STUDENT")).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/student/reservations").param("userId", studentId.toString()).with(user("second").roles("STUDENT")))
                .andExpect(status().isOk()).andExpect(content().string(not(containsString("data-reservation-id=\"" + id + "\""))));
        assertThat(reservations.mine("second", null, 0).total()).isZero();
        assertThat(activeCount()).isEqualTo(1);
    }

    @Test
    void ownListFiltersPaginatesAndDisplaysTimeExpiredStatusWithoutWritingOnGet() throws Exception {
        Long expired = insert(studentId, seatId, TODAY.minusDays(1), 1, "PENDING", TODAY.minusDays(1).atTime(8, 15));
        for (int i = 0; i < 22; i++) insert(studentId, seatId, TODAY.minusDays(2 + i), 1, "CANCELLED", TODAY.minusDays(2 + i).atTime(8, 15));
        var first = reservations.mine("student", null, 0); var second = reservations.mine("student", null, 1);
        assertThat(first.records()).hasSize(20); assertThat(first.hasNext()).isTrue(); assertThat(first.getPages()).isEqualTo(2);
        assertThat(second.records()).hasSize(3); assertThat(second.hasNext()).isFalse();
        assertThat(first.records()).extracting(ReservationSummary::id).doesNotContainAnyElementsOf(second.records().stream().map(ReservationSummary::id).toList());
        assertThat(reservations.mine("student", ReservationStatus.EXPIRED, 0).total()).isEqualTo(1);
        assertThat(reservations.mine("student", ReservationStatus.PENDING, 0).total()).isZero();
        mvc.perform(get("/student/reservations?status=EXPIRED").with(user("student").roles("STUDENT")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("超时释放")));
        assertThat(jdbc.queryForObject("SELECT status FROM reserve_record WHERE id = ?", String.class, expired)).isEqualTo("PENDING");
    }

    @Test
    void gridCanFilterFreeSeatsAndSuppressBookingAfterCutoffOrExistingStudentBooking() throws Exception {
        book("second", seatId, TODAY, 1);
        spaces.setSeatEnabled(roomId, secondSeat, false);
        mvc.perform(get("/student/rooms/" + roomId + "/seats?availableOnly=true").with(user("student").roles("STUDENT")))
                .andExpect(status().isOk()).andExpect(content().string(not(containsString("data-seat-id=\"" + seatId + "\""))))
                .andExpect(content().string(not(containsString("data-seat-id=\"" + secondSeat + "\""))));
        clock.set(TODAY.atTime(11, 30, 1));
        mvc.perform(get("/student/rooms/" + roomId + "/seats").with(user("student").roles("STUDENT")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("已停止预约")))
                .andExpect(content().string(not(containsString("aria-label=\"预约座位"))));
        clock.set(TODAY.atTime(7, 30));
        book("student", spaces.listSeats(roomId).get(2).getId(), TODAY, 1);
        mvc.perform(get("/student/rooms/" + roomId + "/seats").with(user("student").roles("STUDENT")))
                .andExpect(content().string(containsString("你在此日期时段已有预约")))
                .andExpect(content().string(not(containsString("aria-label=\"预约座位"))));
    }

    @Test
    void bookingAndCancelNeedCsrfAndStudentRoleAndBadParametersDoNotWrite() throws Exception {
        String create = "/student/rooms/" + roomId + "/seats/" + seatId + "/reservations";
        mvc.perform(post(create).with(user("student").roles("STUDENT")).param("date", TODAY.toString()).param("slot", "1"))
                .andExpect(status().isForbidden());
        mvc.perform(post(create).with(user("librarian").roles("LIBRARIAN")).with(csrf()).param("date", TODAY.toString()).param("slot", "1"))
                .andExpect(status().isForbidden());
        for (String date : List.of("invalid", TODAY.minusDays(1).toString(), TODAY.plusDays(7).toString())) {
            mvc.perform(post(create).with(user("student").roles("STUDENT")).with(csrf()).param("date", date).param("slot", "1"))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post(create).with(user("student").roles("STUDENT")).with(csrf()).param("date", TODAY.toString()))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/student/reservations?status=UNKNOWN").with(user("student").roles("STUDENT"))).andExpect(status().isBadRequest());
        mvc.perform(get("/student/reservations?page=-1").with(user("student").roles("STUDENT"))).andExpect(status().isBadRequest());
        assertThat(activeCount()).isZero();
        Long id = book("student", seatId, TODAY, 1);
        mvc.perform(post("/student/reservations/" + id + "/cancel").with(user("student").roles("STUDENT"))).andExpect(status().isForbidden());
        mvc.perform(post("/student/reservations/" + id + "/cancel").with(user("admin").roles("SUPER_ADMIN")).with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    void concurrentStudentsCompetingForSameSeatHaveExactlyOneWinner() throws Exception {
        assertThat(race(() -> attempt(() -> book("student", seatId, TODAY, 1)),
                () -> attempt(() -> book("second", seatId, TODAY, 1)))).containsExactlyInAnyOrder(true, false);
        assertThat(activeCount()).isEqualTo(1);
    }

    @Test
    void concurrentSameStudentBookingDifferentRoomsHasExactlyOneWinner() throws Exception {
        Long other = spaces.saveRoom(null, new RoomForm("第二阅览室", "三层", ""));
        Long otherSeat = spaces.saveSeat(other, null, new SeatForm("A01"));
        assertThat(race(() -> attempt(() -> book("student", seatId, TODAY, 1)),
                () -> attempt(() -> reservations.create("student", other, otherSeat, TODAY, 1))))
                .containsExactlyInAnyOrder(true, false);
        assertThat(activeCount()).isEqualTo(1);
    }

    @Test
    void duplicateClicksCreateOnlyOneRecord() throws Exception {
        assertThat(race(() -> attempt(() -> book("student", seatId, TODAY, 1)),
                () -> attempt(() -> book("student", seatId, TODAY, 1)))).containsExactlyInAnyOrder(true, false);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reserve_record", Long.class)).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void bookingAndMaintenanceCannotLeavePendingBookingOnDisabledResource(boolean entireRoom) throws Exception {
        var results = race(() -> attempt(() -> book("student", seatId, TODAY, 1)),
                () -> attempt(() -> { if (entireRoom) spaces.setRoomEnabled(roomId, false); else spaces.setSeatEnabled(roomId, seatId, false); }));
        assertThat(results).containsExactlyInAnyOrder(true, false);
        if (results.get(0)) {
            assertThat(activeCount()).isEqualTo(1);
            assertThat(spaces.room(roomId, false).getStatus()).isEqualTo(1);
            assertThat(spaces.seat(roomId, seatId).getStatus()).isEqualTo(1);
        } else assertThat(activeCount()).isZero();
    }

    @Test
    void bookingAndDeletionCannotLeaveDanglingReservation() throws Exception {
        var results = race(() -> attempt(() -> book("student", seatId, TODAY, 1)),
                () -> attempt(() -> spaces.deleteSeat(roomId, seatId)));
        assertThat(results).containsExactlyInAnyOrder(true, false);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reserve_record r LEFT JOIN seat s ON s.id=r.seat_id WHERE s.id IS NULL", Long.class)).isZero();
        assertThat(activeCount()).isEqualTo(results.get(0) ? 1 : 0);
    }

    @Test
    void cancellationRacingNewBookingPreservesCancelledHistoryAndAtMostOneOccupant() throws Exception {
        Long old = book("student", seatId, TODAY, 1);
        var results = race(() -> reservations.cancel("student", old), () -> attempt(() -> book("second", seatId, TODAY, 1)));
        assertThat(results.get(0)).isTrue();
        assertThat(jdbc.queryForObject("SELECT status FROM reserve_record WHERE id = ?", String.class, old)).isEqualTo("CANCELLED");
        assertThat(activeCount()).isEqualTo(results.get(1) ? 1 : 0);
    }

    @Test
    void expiredCrossRoomHistoryRetriesRolledBackTransactionAndBothBookingsSucceed() throws Exception {
        Long otherRoom = spaces.saveRoom(null, new RoomForm("第二阅览室", "三层", ""));
        Long otherSeat = spaces.saveSeat(otherRoom, null, new SeatForm("A01"));
        insert(studentId, otherSeat, TODAY, 1, "PENDING", TODAY.atTime(8, 15));
        insert(secondStudentId, seatId, TODAY, 1, "PENDING", TODAY.atTime(8, 15));
        clock.set(TODAY.atTime(8, 20));
        assertThat(race(() -> attempt(() -> book("student", seatId, TODAY, 1)),
                () -> attempt(() -> reservations.create("second", otherRoom, otherSeat, TODAY, 1))))
                .containsExactly(true, true);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reserve_record WHERE status = 'EXPIRED'", Long.class)).isEqualTo(2);
        assertThat(activeCount()).isEqualTo(2);
        assertThat(reservations.mine("student", ReservationStatus.PENDING, 0).total()).isEqualTo(1);
        assertThat(reservations.mine("second", ReservationStatus.PENDING, 0).total()).isEqualTo(1);
    }

    @Test
    void conflictRedirectKeepsSelectedDateSlotAndOwnCancellationWorksThroughHttp() throws Exception {
        Long id = book("student", seatId, TODAY, 1);
        mvc.perform(post("/student/rooms/" + roomId + "/seats/" + secondSeat + "/reservations")
                .with(user("student").roles("STUDENT")).with(csrf()).param("date", TODAY.toString()).param("slot", "1"))
                .andExpect(redirectedUrl("/student/rooms/" + roomId + "/seats?date=" + TODAY + "&slot=1"))
                .andExpect(flash().attribute("error", containsString("不能重复预约")));
        mvc.perform(post("/student/reservations/" + id + "/cancel").with(user("student").roles("STUDENT")).with(csrf()))
                .andExpect(redirectedUrl("/student/reservations")).andExpect(flash().attribute("notice", containsString("座位已重新开放")));
        assertThat(reservations.mine("student", ReservationStatus.CANCELLED, 0).total()).isEqualTo(1);
    }
}
