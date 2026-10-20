package com.seapullkb.library.service;

import com.seapullkb.library.mapper.ReservationMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ReservationMaintenance {
    private static final Logger LOG = LoggerFactory.getLogger(ReservationMaintenance.class);
    private final ReservationMapper records;
    private final ReservationLifecycleService lifecycle;
    private final Clock clock;
    public ReservationMaintenance(ReservationMapper records, ReservationLifecycleService lifecycle, Clock clock) {
        this.records = records; this.lifecycle = lifecycle; this.clock = clock;
    }
    public SweepResult runOnce() {
        LocalDateTime scanTime = LocalDateTime.now(clock);
        long cursor = 0;
        int scanned = 0, expired = 0, completed = 0, failed = 0;
        while (true) {
            var ids = records.dueIds(cursor, scanTime);
            if (ids.isEmpty()) break;
            for (Long id : ids) {
                cursor = id; scanned++;
                try {
                    LifecycleResult result = lifecycle.processDue(id);
                    if (result == LifecycleResult.EXPIRED) expired++;
                    if (result == LifecycleResult.AUTO_COMPLETED) completed++;
                } catch (RuntimeException failure) {
                    failed++;
                    LOG.error("Reservation maintenance failed for record {}; will retry in the next sweep", id, failure);
                }
            }
        }
        return new SweepResult(scanned, expired, completed, failed);
    }
    public record SweepResult(int scanned, int expired, int completed, int failed) { }
}
