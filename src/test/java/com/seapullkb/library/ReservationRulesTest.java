package com.seapullkb.library;

import com.seapullkb.library.entity.TimeSlot;
import com.seapullkb.library.service.ReservationConflictException;
import com.seapullkb.library.service.ReservationRules;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.assertj.core.api.Assertions.*;

class ReservationRulesTest {
    private final ReservationRules rules = new ReservationRules();
    private final LocalDate date = LocalDate.of(2030, 5, 8);

    @ParameterizedTest
    @EnumSource(TimeSlot.class)
    void exactlyThirtyMinutesBeforeEndIsAllowedButLaterIsRejected(TimeSlot slot) {
        var cutoff = slot.endsOn(date).minusMinutes(30);
        assertThatCode(() -> rules.requireOpen(date, slot, cutoff)).doesNotThrowAnyException();
        assertThatThrownBy(() -> rules.requireOpen(date, slot, cutoff.plusNanos(1)))
                .isInstanceOf(ReservationConflictException.class);
        assertThatThrownBy(() -> rules.requireOpen(date, slot, slot.endsOn(date)))
                .isInstanceOf(ReservationConflictException.class);
    }

    @Test
    void onlySevenNaturalDaysAreAllowedIncludingAcrossMonthBoundary() {
        var now = LocalDate.of(2030, 5, 30).atTime(23, 59, 59);
        for (int i = 0; i < 7; i++) {
            var selected = now.toLocalDate().plusDays(i);
            assertThatCode(() -> rules.validateDate(selected, now)).doesNotThrowAnyException();
        }
        assertThatThrownBy(() -> rules.validateDate(now.toLocalDate().minusDays(1), now)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> rules.validateDate(now.toLocalDate().plusDays(7), now)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> rules.validateDate(null, now)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ordinaryDeadlineAndLateBookingTenMinuteDeadlineAreDifferent() {
        assertThat(rules.checkinDeadline(date, TimeSlot.MORNING, date.atTime(7, 30))).isEqualTo(date.atTime(8, 15));
        assertThat(rules.checkinDeadline(date, TimeSlot.MORNING, date.atTime(8, 15))).isEqualTo(date.atTime(8, 15));
        assertThat(rules.checkinDeadline(date, TimeSlot.MORNING, date.atTime(8, 15, 1))).isEqualTo(date.atTime(8, 25, 1));
        assertThat(rules.checkinDeadline(date, TimeSlot.MORNING, date.atTime(8, 20))).isEqualTo(date.atTime(8, 30));
        assertThat(rules.checkinDeadline(date.plusDays(6), TimeSlot.EVENING, date.atTime(20, 0)))
                .isEqualTo(date.plusDays(6).atTime(19, 15));
    }
}
