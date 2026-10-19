package com.seapullkb.library;

import com.seapullkb.library.service.*;
import java.time.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Testcontainers
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
@Import(UiIntegrationTest.TimeConfiguration.class)
class UiIntegrationTest {
    static final LocalDate DAY=LocalDate.of(2030,5,8);
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.0").withDatabaseName("ui_test");
    @DynamicPropertySource static void database(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url",MYSQL::getJdbcUrl);p.add("spring.datasource.username",MYSQL::getUsername);p.add("spring.datasource.password",MYSQL::getPassword);
    }
    static class MutableClock extends Clock {
        final AtomicReference<Instant> value=new AtomicReference<>();
        void at(LocalDateTime time) { value.set(time.atZone(getZone()).toInstant()); }
        public ZoneId getZone() { return ZoneId.of("Asia/Shanghai"); }
        public Clock withZone(ZoneId zone) { return Clock.fixed(instant(),zone); }
        public Instant instant() { return value.get(); }
    }
    @TestConfiguration static class TimeConfiguration {
        @Bean @Primary MutableClock uiClock() { var c=new MutableClock();c.at(DAY.atTime(8,0));return c; }
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired InitialDataService initial;
    @Autowired DashboardService dashboards;
    @Autowired ReservationService reservations;
    @Autowired ReservationLifecycleService lifecycle;
    @Autowired AccountService accounts;
    @Autowired MutableClock clock;
    Long room,seat;
    @BeforeEach void seed() {
        jdbc.update("DELETE FROM reserve_record");jdbc.update("DELETE FROM seat");jdbc.update("DELETE FROM reading_room");jdbc.update("DELETE FROM `user`");
        clock.at(DAY.atTime(8,0));initial.initialize(true,"Ui-test-password-123!");
        room=jdbc.queryForObject("SELECT MIN(id) FROM reading_room",Long.class);seat=jdbc.queryForObject("SELECT MIN(id) FROM seat",Long.class);
    }
    @Test void emptyStudentDashboardRendersRealCapacityAndNoFixtureNumbers() throws Exception {
        var d=dashboards.student("student",DAY,1);
        assertThat(d.catalog().rooms()).hasSize(1);assertThat(d.catalog().getEnabledSeats()).isEqualTo(20);
        assertThat(d.catalog().getAvailableSeats()).isEqualTo(20);assertThat(d.getNext()).isNull();
        mvc.perform(get("/student").with(user("student").roles("STUDENT"))).andExpect(status().isOk())
                .andExpect(content().string(containsString("还没有待办预约"))).andExpect(content().string(containsString("data-metric=\"available\">20")));
    }
    @Test void activeReservationAndCompletedSlotHaveDifferentDashboardMeaning() {
        Long id=reservations.create("student",room,seat,DAY,1);
        assertThat(dashboards.student("student",DAY,1).getNext().id()).isEqualTo(id);
        assertThat(dashboards.catalog(DAY,1).getAvailableSeats()).isEqualTo(19);
        lifecycle.checkIn("student",id);lifecycle.checkOut("student",id);
        var d=dashboards.student("student",DAY,1);assertThat(d.active()).isEmpty();
        assertThat(d.catalog().getAvailableSeats()).isEqualTo(19);
        assertThat(dashboards.catalog(DAY,2).getAvailableSeats()).isEqualTo(20);
    }
    @Test void effectiveExpiryDisappearsWithoutReadSideWritesAndFreesCapacity() {
        Long id=reservations.create("student",room,seat,DAY,1);clock.at(DAY.atTime(8,15,1));
        assertThat(dashboards.student("student",DAY,1).active()).isEmpty();
        assertThat(dashboards.catalog(DAY,1).getAvailableSeats()).isEqualTo(20);
        assertThat(jdbc.queryForObject("SELECT status FROM reserve_record WHERE id=?",String.class,id)).isEqualTo("PENDING");
    }
    @Test void exactDeadlineStillCountsAsPendingAndUnavailable() {
        reservations.create("student",room,seat,DAY,1);clock.at(DAY.atTime(8,15));
        var d=dashboards.student("student",DAY,1);assertThat(d.active()).hasSize(1);
        assertThat(d.getNext().canCheckIn(LocalDateTime.now(clock))).isTrue();assertThat(d.catalog().getAvailableSeats()).isEqualTo(19);
    }
    @Test void disabledRoomsAndSeatsNeverInflateStudentAvailability() {
        jdbc.update("UPDATE seat SET status=0 WHERE id=?",seat);
        assertThat(dashboards.catalog(DAY,1).getEnabledSeats()).isEqualTo(19);
        jdbc.update("UPDATE reading_room SET status=0 WHERE id=?",room);
        assertThat(dashboards.catalog(DAY,1).rooms()).isEmpty();
        assertThat(dashboards.catalog(DAY,1).getAvailableSeats()).isZero();
    }
    @Test void upcomingReservationIsOwnAndActiveListDoesNotDependOnHistoryPagination() {
        reservations.create("student",room,seat,DAY.plusDays(1),1);
        jdbc.update("INSERT INTO reserve_record(user_id,seat_id,reserve_date,time_slot,status,checkin_deadline) SELECT user_id,seat_id,reserve_date,time_slot,'CANCELLED',checkin_deadline FROM reserve_record");
        var d=dashboards.student("student",DAY,1);assertThat(d.active()).hasSize(1);assertThat(d.getNext().reserveDate()).isEqualTo(DAY.plusDays(1));
        assertThatThrownBy(()->dashboards.student("librarian",DAY,1)).isInstanceOf(AccessDeniedException.class);
    }
    @Test void defaultSlotAdvancesWithClockAndAfterHoursDefaultsToTomorrow() {
        clock.at(DAY.atTime(13,0));assertThat(dashboards.catalog(null,null).slot().getCode()).isEqualTo(2);
        clock.at(DAY.atTime(21,31));var c=dashboards.catalog(null,null);
        assertThat(c.date()).isEqualTo(DAY.plusDays(1));assertThat(c.slot().getCode()).isEqualTo(1);
        assertThat(dashboards.catalog(DAY,3).bookingOpen()).isFalse();
    }
    @Test void dashboardRejectsUnsupportedDatesSlotsAndOtherRoles() throws Exception {
        for(String date:new String[]{DAY.minusDays(1).toString(),DAY.plusDays(7).toString()})
            mvc.perform(get("/student").param("date",date).with(user("student").roles("STUDENT"))).andExpect(status().isBadRequest());
        mvc.perform(get("/student").param("slot","4").with(user("student").roles("STUDENT"))).andExpect(status().isBadRequest());
        mvc.perform(get("/student").with(user("librarian").roles("LIBRARIAN"))).andExpect(status().isForbidden());
    }
    @Test void roomCatalogPreservesSelectedSlotAndShowsActualFreeSeats() throws Exception {
        reservations.create("student",room,seat,DAY,1);
        mvc.perform(get("/student/rooms").param("date",DAY.toString()).param("slot","1").with(user("student").roles("STUDENT")))
                .andExpect(status().isOk()).andExpect(content().string(containsString(">19</strong>")))
                .andExpect(content().string(containsString("date=2030-05-08&amp;slot=1")));
        mvc.perform(get("/student/rooms").param("date",DAY.plusDays(7).toString()).with(user("student").roles("STUDENT")))
                .andExpect(status().isBadRequest());
    }

}
