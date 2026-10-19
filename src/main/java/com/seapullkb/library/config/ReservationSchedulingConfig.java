package com.seapullkb.library.config;

import com.seapullkb.library.service.ReservationMaintenance;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.reservation-scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class ReservationSchedulingConfig {
    @Bean
    public ReservationScheduler reservationScheduler(ReservationMaintenance maintenance) {
        return new ReservationScheduler(maintenance);
    }
}
