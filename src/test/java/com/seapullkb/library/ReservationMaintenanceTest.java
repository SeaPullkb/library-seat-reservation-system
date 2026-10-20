package com.seapullkb.library;

import com.seapullkb.library.mapper.ReservationMapper;
import com.seapullkb.library.service.*;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReservationMaintenanceTest {
    @Test
    void failedRecordDoesNotPreventAnotherRecordFromBeingProcessed() {
        var records = mock(ReservationMapper.class); var lifecycle = mock(ReservationLifecycleService.class);
        var clock = Clock.fixed(Instant.parse("2030-05-08T00:20:00Z"), ZoneId.of("Asia/Shanghai"));
        var now = LocalDateTime.now(clock);
        when(records.dueIds(0L, now)).thenReturn(List.of(1L, 2L));
        when(records.dueIds(2L, now)).thenReturn(List.of());
        when(lifecycle.processDue(1L)).thenThrow(new IllegalStateException("Deliberate isolated test failure"));
        when(lifecycle.processDue(2L)).thenReturn(LifecycleResult.EXPIRED);
        var result = new ReservationMaintenance(records, lifecycle, clock).runOnce();
        assertThat(result.scanned()).isEqualTo(2); assertThat(result.failed()).isEqualTo(1);
        assertThat(result.expired()).isEqualTo(1); verify(lifecycle).processDue(2L);
    }
}
