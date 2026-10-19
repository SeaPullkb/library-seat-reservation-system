package com.seapullkb.library.config;

import com.seapullkb.library.service.ReservationMaintenance;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;

public class ReservationScheduler {
    private static final Logger LOG = LoggerFactory.getLogger(ReservationScheduler.class);
    private final ReservationMaintenance maintenance;
    private final AtomicBoolean running = new AtomicBoolean();
    public ReservationScheduler(ReservationMaintenance maintenance) { this.maintenance = maintenance; }

    @EventListener(ApplicationReadyEvent.class)
    public void catchUpOnStartup() { sweep(); }

    @Scheduled(fixedDelayString = "${app.reservation-scheduler.interval-ms:60000}",
               initialDelayString = "${app.reservation-scheduler.initial-delay-ms:60000}")
    public void sweep() {
        if (!running.compareAndSet(false, true)) return;
        try {
            var result = maintenance.runOnce();
            if (result.scanned() > 0) LOG.info("Reservation maintenance: scanned={}, expired={}, completed={}, failed={}",
                    result.scanned(), result.expired(), result.completed(), result.failed());
        } catch (RuntimeException failure) {
            LOG.error("Reservation sweep failed; will retry on the next scheduled execution", failure);
        } finally { running.set(false); }
    }
}
