package com.seapullkb.library;

import com.seapullkb.library.config.ReservationScheduler;
import java.sql.DriverManager;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties = {"app.reservation-scheduler.enabled=true", "app.reservation-scheduler.interval-ms=100", "app.reservation-scheduler.initial-delay-ms=2000"})
@ActiveProfiles("test")
@Testcontainers
@Import(SchedulerIntegrationTest.TimeConfiguration.class)
class SchedulerIntegrationTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("scheduler_test").withInitScript("db/schema.sql");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) throws Exception {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        // Prepare due data before Spring publishes ApplicationReadyEvent.
        try (var connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())) {
            try (var statement = connection.prepareStatement("INSERT INTO `user`(id,username,password,real_name,phone,role) VALUES (1,'scheduler_fixture',?,'任务验收','13800000000','STUDENT')")) {
                statement.setString(1, new BCryptPasswordEncoder().encode("Scheduler-fixture-123!")); statement.executeUpdate();
            }
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO reading_room(id,room_name,location) VALUES (1,'测试阅览室','测试层')");
                statement.executeUpdate("INSERT INTO seat(id,room_id,seat_no) VALUES (1,1,'A01')");
                statement.executeUpdate("INSERT INTO reserve_record(id,user_id,seat_id,reserve_date,time_slot,status,checkin_deadline) VALUES (1,1,1,'2030-05-08',1,'PENDING','2030-05-08 08:15:00')");
                statement.executeUpdate("INSERT INTO reserve_record(id,user_id,seat_id,reserve_date,time_slot,status,checkin_deadline,checkin_at) VALUES (2,1,1,'2030-05-07',3,'USING','2030-05-07 19:15:00','2030-05-07 19:01:00')");
            }
        }
    }
    @TestConfiguration
    static class TimeConfiguration {
        @Bean @Primary
        Clock schedulerClock() { return Clock.fixed(Instant.parse("2030-05-08T00:20:00Z"), ZoneId.of("Asia/Shanghai")); }
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired ReservationScheduler scheduler;
    @Autowired ScheduledAnnotationBeanPostProcessor scheduledTasks;

    @Test
    void startupCatchupAndRealScheduledExecutionUseTheSameStateTransitions() {
        assertThat(scheduler).isNotNull();
        assertThat(jdbc.queryForObject("SELECT status FROM reserve_record WHERE id=1", String.class)).isEqualTo("EXPIRED");
        assertThat(jdbc.queryForObject("SELECT status FROM reserve_record WHERE id=2", String.class)).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT checkout_at FROM reserve_record WHERE id=2", LocalDateTime.class)).isEqualTo(LocalDateTime.of(2030, 5, 7, 22, 0));
        assertThat(scheduledTasks.getScheduledTasks()).anySatisfy(task -> {
            assertThat(task.getTask()).isInstanceOf(FixedDelayTask.class);
            assertThat(((FixedDelayTask) task.getTask()).getIntervalDuration()).isEqualTo(Duration.ofMillis(100));
        });
        jdbc.update("INSERT INTO reserve_record(id,user_id,seat_id,reserve_date,time_slot,status,checkin_deadline) VALUES (3,1,1,'2030-05-08',1,'PENDING','2030-05-08 08:15:00')");
        // No direct maintenance call: wait for Spring's actual scheduled callback.
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(jdbc.queryForObject("SELECT status FROM reserve_record WHERE id=3", String.class)).isEqualTo("EXPIRED"));
        assertThat(jdbc.queryForObject("SELECT released_at FROM reserve_record WHERE id=3", LocalDateTime.class)).isEqualTo(LocalDateTime.of(2030, 5, 8, 8, 20));
    }
}
