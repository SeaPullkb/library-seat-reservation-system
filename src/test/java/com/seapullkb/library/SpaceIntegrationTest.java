package com.seapullkb.library;

import com.seapullkb.library.service.*;
import com.seapullkb.library.web.RoomForm;
import com.seapullkb.library.web.SeatForm;
import java.time.*;
import java.util.List;
import java.util.concurrent.*;
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
@Import(SpaceIntegrationTest.FixedTime.class)
class SpaceIntegrationTest {
    private static final LocalDate TODAY = LocalDate.of(2030, 5, 8);
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0").withDatabaseName("space_test");
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }
    @TestConfiguration
    static class FixedTime {
        @Bean @Primary
        Clock fixedClock() {
            return Clock.fixed(TODAY.atTime(8, 10).atZone(ZoneId.of("Asia/Shanghai")).toInstant(), ZoneId.of("Asia/Shanghai"));
        }
    }

    @Autowired SpaceService spaces;
    @Autowired InitialDataService initialData;
    @Autowired AccountService accounts;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    Long roomId;
    Long seatId;
    Long userId;

    @BeforeEach
    void seed() {
        jdbc.update("DELETE FROM reserve_record"); jdbc.update("DELETE FROM seat");
        jdbc.update("DELETE FROM reading_room"); jdbc.update("DELETE FROM `user`");
        initialData.initialize(true, "Integration-test-123!");
        roomId = jdbc.queryForObject("SELECT MIN(id) FROM reading_room", Long.class);
        seatId = jdbc.queryForObject("SELECT MIN(id) FROM seat", Long.class);
        userId = accounts.findByUsername("student").getId();
    }

    private Long record(Long seat, String status, LocalDate date, int slot, LocalDateTime deadline) {
        jdbc.update("INSERT INTO reserve_record (user_id,seat_id,reserve_date,time_slot,status,checkin_deadline) VALUES (?,?,?,?,?,?)",
                userId, seat, date, slot, status, deadline);
        return jdbc.queryForObject("SELECT MAX(id) FROM reserve_record", Long.class);
    }

    @Test
    void adminCanCreateEditAndDisableRoomThroughValidatedForms() throws Exception {
        mvc.perform(post("/admin/rooms").with(user("librarian").roles("LIBRARIAN")).with(csrf())
                .param("name", "  第二阅览室  ").param("location", " 三层 ").param("description", "安静空间"))
                .andExpect(redirectedUrl("/admin/rooms"));
        Long id = jdbc.queryForObject("SELECT MAX(id) FROM reading_room", Long.class);
        assertThat(spaces.room(id, false).getRoomName()).isEqualTo("第二阅览室");
        mvc.perform(post("/admin/rooms/" + id).with(user("admin").roles("SUPER_ADMIN")).with(csrf())
                .param("name", "新名称").param("location", "四层").param("description", "更新介绍").param("status", "0"))
                .andExpect(redirectedUrl("/admin/rooms"));
        assertThat(spaces.room(id, false).getStatus()).isEqualTo(1);
        mvc.perform(post("/admin/rooms/" + id + "/status").with(user("librarian").roles("LIBRARIAN"))
                .with(csrf()).param("enabled", "false")).andExpect(redirectedUrl("/admin/rooms"));
        assertThat(spaces.room(id, false).getStatus()).isZero();
        spaces.setRoomEnabled(id, true);
        assertThat(spaces.room(id, true).getStatus()).isEqualTo(1);
    }

    @Test
    void invalidRoomAndSeatFormsKeepInputAndDisplayValidationInModal() throws Exception {
        mvc.perform(post("/admin/rooms").with(user("librarian").roles("LIBRARIAN")).with(csrf())
                .param("name", " ").param("location", "保留的位置"))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("roomForm", "name"))
                .andExpect(content().string(containsString("data-auto-show=\"true\"")))
                .andExpect(content().string(containsString("保留的位置")));
        mvc.perform(post("/admin/rooms/" + roomId + "/seats").with(user("librarian").roles("LIBRARIAN"))
                .with(csrf()).param("seatNo", "A".repeat(21)))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("seatForm", "seatNo"));
        assertThat(spaces.listRooms(false)).hasSize(1);
        assertThat(spaces.listSeats(roomId)).hasSize(20);
    }

    @Test
    void seatNumbersAreUniqueWithinRoomIncludingAfterTrimmingAndRename() throws Exception {
        mvc.perform(post("/admin/rooms/" + roomId + "/seats").with(user("librarian").roles("LIBRARIAN"))
                .with(csrf()).param("seatNo", " a01 "))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("seatForm", "seatNo"))
                .andExpect(content().string(containsString("已存在此座位编号")));
        Long added = spaces.saveSeat(roomId, null, new SeatForm("B01"));
        assertThatThrownBy(() -> spaces.saveSeat(roomId, added, new SeatForm("A01")))
                .isInstanceOf(MaintenanceConflictException.class);
        assertThat(spaces.seat(roomId, added).getSeatNo()).isEqualTo("B01");
        Long other = spaces.saveRoom(null, new RoomForm("第二阅览室", "三层", ""));
        assertThat(spaces.saveSeat(other, null, new SeatForm("A01"))).isNotNull();
    }

    @Test
    void studentCapacityUsesEnabledSeatsAndDoesNotStoreDuplicateCapacity() {
        spaces.setSeatEnabled(roomId, seatId, false);
        var room = spaces.listRooms(true).get(0);
        assertThat(room.enabledSeats()).isEqualTo(19);
        assertThat(room.totalSeats()).isEqualTo(20);
        spaces.setRoomEnabled(roomId, false);
        assertThat(spaces.listRooms(true)).isEmpty();
        assertThat(spaces.listRooms(false)).hasSize(1);
        spaces.setRoomEnabled(roomId, true);
        assertThat(spaces.listRooms(true).get(0).enabledSeats()).isEqualTo(19);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "USING"})
    void currentAndFutureEffectiveBookingsBlockSeatAndWholeRoomDisable(String state) throws Exception {
        record(seatId, state, TODAY, 1, TODAY.atTime(8, 15));
        assertThatThrownBy(() -> spaces.setSeatEnabled(roomId, seatId, false)).isInstanceOf(MaintenanceConflictException.class);
        assertThatThrownBy(() -> spaces.setRoomEnabled(roomId, false)).isInstanceOf(MaintenanceConflictException.class);
        mvc.perform(post("/admin/rooms/" + roomId + "/seats/" + seatId + "/status")
                .with(user("librarian").roles("LIBRARIAN")).with(csrf()).param("enabled", "false"))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attribute("error", containsString("先处理")));
        assertThat(spaces.seat(roomId, seatId).getStatus()).isEqualTo(1);
        assertThat(spaces.room(roomId, false).getStatus()).isEqualTo(1);
        jdbc.update("DELETE FROM reserve_record");
        record(seatId, "PENDING", TODAY.plusDays(3), 2, TODAY.plusDays(3).atTime(14, 15));
        assertThatThrownBy(() -> spaces.setRoomEnabled(roomId, false)).isInstanceOf(MaintenanceConflictException.class);
    }

    @Test
    void overduePendingAndFinishedSlotsDoNotBlockMaintenanceButKeepHistory() {
        record(seatId, "PENDING", TODAY, 1, TODAY.atTime(8, 9));
        record(seatId, "USING", TODAY.minusDays(1), 3, TODAY.minusDays(1).atTime(19, 15));
        spaces.setSeatEnabled(roomId, seatId, false);
        spaces.setRoomEnabled(roomId, false);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reserve_record", Integer.class)).isEqualTo(2);
        assertThatThrownBy(() -> spaces.deleteSeat(roomId, seatId)).isInstanceOf(MaintenanceConflictException.class);
    }

    @Test
    void deadlineInstantIsStillEffectiveButPastSlotsDoNotBlock() {
        record(seatId, "PENDING", TODAY, 1, TODAY.atTime(8, 10));
        assertThatThrownBy(() -> spaces.setSeatEnabled(roomId, seatId, false)).isInstanceOf(MaintenanceConflictException.class);
        assertThat(spaces.studentGrid(roomId, TODAY, 1, userId).get(0).availability()).isEqualTo("MINE");
        jdbc.update("DELETE FROM reserve_record");
        record(seatId, "USING", TODAY.minusDays(1), 1, TODAY.minusDays(1).atTime(8, 15));
        spaces.setRoomEnabled(roomId, false);
        assertThat(spaces.room(roomId, false).getStatus()).isZero();
    }

    @Test
    void historicalSeatCanBeRenamedButNeverDeleted() throws Exception {
        Long reservation = record(seatId, "COMPLETED", TODAY.minusDays(1), 1, TODAY.minusDays(1).atTime(8, 15));
        spaces.saveSeat(roomId, seatId, new SeatForm("Z99"));
        assertThat(jdbc.queryForObject("SELECT seat_id FROM reserve_record WHERE id = ?", Long.class, reservation)).isEqualTo(seatId);
        assertThat(spaces.seat(roomId, seatId).getSeatNo()).isEqualTo("Z99");
        mvc.perform(post("/admin/rooms/" + roomId + "/seats/" + seatId + "/delete")
                .with(user("librarian").roles("LIBRARIAN")).with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attribute("error", containsString("保留历史")));
        spaces.setSeatEnabled(roomId, seatId, false);
        assertThat(spaces.seat(roomId, seatId).getStatus()).isZero();
    }

    @Test
    void unreferencedSeatCanBeCreatedRenamedEnabledDisabledAndDeletedThroughHttp() throws Exception {
        String base = "/admin/rooms/" + roomId + "/seats";
        mvc.perform(post(base).with(user("librarian").roles("LIBRARIAN")).with(csrf()).param("seatNo", "B01"))
                .andExpect(redirectedUrl(base));
        Long added = jdbc.queryForObject("SELECT id FROM seat WHERE seat_no = 'B01'", Long.class);
        mvc.perform(post(base + "/" + added).with(user("librarian").roles("LIBRARIAN"))
                .with(csrf()).param("seatNo", "B02").param("roomId", "999999").param("status", "0"))
                .andExpect(redirectedUrl(base));
        assertThat(spaces.seat(roomId, added).getRoomId()).isEqualTo(roomId);
        assertThat(spaces.seat(roomId, added).getStatus()).isEqualTo(1);
        spaces.setSeatEnabled(roomId, added, false); spaces.setSeatEnabled(roomId, added, true);
        mvc.perform(post(base + "/" + added + "/delete").with(user("admin").roles("SUPER_ADMIN")).with(csrf()))
                .andExpect(redirectedUrl(base));
        assertThatThrownBy(() -> spaces.seat(roomId, added)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void gridUsesSelectedDateAndSlotAndAllFiveReservationStates() {
        var seats = spaces.listSeats(roomId);
        record(seats.get(0).getId(), "PENDING", TODAY, 1, TODAY.atTime(8, 15));
        record(seats.get(1).getId(), "USING", TODAY, 1, TODAY.atTime(8, 15));
        record(seats.get(2).getId(), "COMPLETED", TODAY, 1, TODAY.atTime(8, 15));
        record(seats.get(3).getId(), "CANCELLED", TODAY, 1, TODAY.atTime(8, 15));
        record(seats.get(4).getId(), "EXPIRED", TODAY, 1, TODAY.atTime(8, 15));
        record(seats.get(5).getId(), "PENDING", TODAY, 1, TODAY.atTime(8, 9));
        spaces.setSeatEnabled(roomId, seats.get(6).getId(), false);
        var grid = spaces.studentGrid(roomId, TODAY, 1, accounts.findByUsername("admin").getId());
        assertThat(grid.subList(0, 7)).extracting(SeatView::availability)
                .containsExactly("OCCUPIED", "OCCUPIED", "OCCUPIED", "FREE", "FREE", "FREE", "DISABLED");
        var mine = spaces.studentGrid(roomId, TODAY, 1, userId);
        assertThat(mine.subList(0, 3)).extracting(SeatView::availability).containsOnly("MINE");
        assertThat(spaces.studentGrid(roomId, TODAY, 2, userId)).filteredOn(s -> !s.availability().equals("DISABLED"))
                .extracting(SeatView::availability).containsOnly("FREE");
        assertThat(spaces.studentGrid(roomId, TODAY.plusDays(1), 1, userId)).filteredOn(s -> !s.availability().equals("DISABLED"))
                .extracting(SeatView::availability).containsOnly("FREE");
    }

    @Test
    void oldExpiredAndReplacementPendingRenderAsOneSeatWithoutDoubleCounting() {
        record(seatId, "EXPIRED", TODAY, 1, TODAY.atTime(8, 5));
        record(seatId, "PENDING", TODAY, 1, TODAY.atTime(8, 20));
        var grid = spaces.studentGrid(roomId, TODAY, 1, userId);
        assertThat(grid).hasSize(20);
        assertThat(grid.get(0).availability()).isEqualTo("MINE");
    }

    @Test
    void pagesAndEditModalsRenderAndStudentCannotSeeDisabledRoom() throws Exception {
        for (String path : List.of("/admin/rooms", "/admin/rooms/" + roomId + "/edit",
                "/admin/rooms/" + roomId + "/seats", "/admin/rooms/" + roomId + "/seats/" + seatId + "/edit")) {
            mvc.perform(get(path).with(user("librarian").roles("LIBRARIAN"))).andExpect(status().isOk());
        }
        mvc.perform(get("/student/rooms").with(user("student").roles("STUDENT")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("第一阅览室")));
        mvc.perform(get("/student/rooms/" + roomId + "/seats").with(user("student").roles("STUDENT")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("A01")))
                .andExpect(content().string(containsString("预约座位A01")));
        spaces.setRoomEnabled(roomId, false);
        mvc.perform(get("/student/rooms").with(user("student").roles("STUDENT")))
                .andExpect(status().isOk()).andExpect(content().string(not(containsString("第一阅览室"))));
        mvc.perform(get("/student/rooms/" + roomId + "/seats").with(user("student").roles("STUDENT")))
                .andExpect(status().isNotFound());
    }

    @Test
    void studentsCannotWriteAdminResourcesAndCsrfIsRequired() throws Exception {
        for (String path : List.of("/admin/rooms", "/admin/rooms/" + roomId,
                "/admin/rooms/" + roomId + "/status", "/admin/rooms/" + roomId + "/seats",
                "/admin/rooms/" + roomId + "/seats/" + seatId,
                "/admin/rooms/" + roomId + "/seats/" + seatId + "/status",
                "/admin/rooms/" + roomId + "/seats/" + seatId + "/delete")) {
            mvc.perform(post(path).with(user("student").roles("STUDENT")).with(csrf())).andExpect(status().isForbidden());
            mvc.perform(post(path).with(user("librarian").roles("LIBRARIAN"))).andExpect(status().isForbidden());
        }
        assertThat(spaces.listSeats(roomId)).hasSize(20);
    }

    @Test
    void badDatesSlotsMissingRoomsAndWrongRoomSeatIdsReturnUsefulErrors() throws Exception {
        for (String query : List.of("?date=2030-05-07", "?date=2030-05-15", "?slot=9", "?date=invalid", "?slot=bad")) {
            mvc.perform(get("/student/rooms/" + roomId + "/seats" + query).with(user("student").roles("STUDENT")))
                    .andExpect(status().isBadRequest()).andExpect(content().string(containsString("无法完成操作")));
        }
        mvc.perform(get("/student/rooms/99999999/seats").with(user("student").roles("STUDENT")))
                .andExpect(status().isNotFound());
        Long other = spaces.saveRoom(null, new RoomForm("第二阅览室", "三层", ""));
        mvc.perform(post("/admin/rooms/" + other + "/seats/" + seatId + "/delete")
                .with(user("librarian").roles("LIBRARIAN")).with(csrf())).andExpect(status().isNotFound());
        assertThat(spaces.seat(roomId, seatId)).isNotNull();
    }

    @Test
    void roomAndSeatNamesAreHtmlEscaped() throws Exception {
        spaces.saveRoom(roomId, new RoomForm("<script>room</script>", "位置", "<b>介绍</b>"));
        spaces.saveSeat(roomId, seatId, new SeatForm("<b>seat</b>"));
        mvc.perform(get("/student/rooms").with(user("student").roles("STUDENT")))
                .andExpect(content().string(containsString("&lt;script&gt;room&lt;/script&gt;")))
                .andExpect(content().string(not(containsString("<script>room</script>"))));
        mvc.perform(get("/student/rooms/" + roomId + "/seats").with(user("student").roles("STUDENT")))
                .andExpect(content().string(containsString("&lt;b&gt;seat&lt;/b&gt;")));
    }

    @Test
    void concurrentSeatCreationInSameRoomHasOneWinner() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            var results = new java.util.ArrayList<Future<Boolean>>();
            for (int i = 0; i < 2; i++) results.add(executor.submit(() -> {
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("并发测试未开始");
                try { spaces.saveSeat(roomId, null, new SeatForm("RACE01")); return true; }
                catch (MaintenanceConflictException ex) { return false; }
            }));
            start.countDown();
            assertThat(List.of(results.get(0).get(20, TimeUnit.SECONDS), results.get(1).get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM seat WHERE room_id = ? AND seat_no = 'RACE01'", Integer.class, roomId)).isEqualTo(1);
        } finally { executor.shutdownNow(); }
    }
}
