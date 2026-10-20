package validation;

import com.seapullkb.library.LibraryApplication;
import com.seapullkb.library.service.ReservationService;
import com.seapullkb.library.service.ReservationMaintenance;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.boot.SpringApplication;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** External validation harness, never included in the production application. */
public class BrowserHarness {
    public static final LocalDate DAY = LocalDate.of(2030, 5, 8);
    public static class ControlledClock extends Clock {
        private final AtomicReference<Instant> time = new AtomicReference<>();
        public void at(LocalDateTime value) { time.set(value.atZone(getZone()).toInstant()); }
        public ZoneId getZone() { return ZoneId.of("Asia/Shanghai"); }
        public Instant instant() { return time.get(); }
        public Clock withZone(ZoneId zone) { return Clock.fixed(instant(), zone); }
    }
    @Configuration
    public static class ClockSettings {
        @Bean @Primary
        ControlledClock validationClock() { var clock = new ControlledClock(); clock.at(DAY.atTime(7, 30)); return clock; }
    }
    public static void main(String[] args) throws Exception {
        var application = new SpringApplication(LibraryApplication.class, ClockSettings.class);
        try (var context = application.run("--spring.profiles.active=dev", "--app.reservation-scheduler.enabled=false", "--server.port="+System.getenv().getOrDefault("SERVER_PORT","8081"))) {
            var jdbc = context.getBean(JdbcTemplate.class);
            Long room = jdbc.queryForObject("SELECT MIN(id) FROM reading_room", Long.class);
            var clock = context.getBean(ControlledClock.class);
            var json = new ObjectMapper();
            Files.writeString(Path.of(args[0]), json.writeValueAsString(Map.of("room", room)));
            var input = new BufferedReader(new InputStreamReader(System.in));
            int sequence = 0;
            for (String line; (line = input.readLine()) != null;) {
                if (line.equals("EXIT")) break;
                Object result;
                if (line.startsWith("TIME ")) { clock.at(LocalDateTime.parse(line.substring(5))); result = "time changed"; }
                else if (line.equals("SWEEP")) result = context.getBean(ReservationMaintenance.class).runOnce();
                else throw new IllegalArgumentException("Unknown harness command");
                Files.writeString(Path.of(args[1]), json.writeValueAsString(Map.of("sequence", ++sequence, "result", result)));
            }
        }
    }
}
