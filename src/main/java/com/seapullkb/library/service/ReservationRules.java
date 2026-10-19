package com.seapullkb.library.service;

import com.seapullkb.library.entity.TimeSlot;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

@Component
public class ReservationRules {
    public void validateDate(LocalDate date, LocalDateTime now) {
        if (date == null || date.isBefore(now.toLocalDate()) || date.isAfter(now.toLocalDate().plusDays(6)))
            throw new IllegalArgumentException("只能预约今天及未来6天，共7个自然日。");
    }
    public boolean isOpen(LocalDate date, TimeSlot slot, LocalDateTime now) {
        return !now.isAfter(slot.endsOn(date).minusMinutes(30));
    }
    public void requireOpen(LocalDate date, TimeSlot slot, LocalDateTime now) {
        validateDate(date, now);
        if (!isOpen(date, slot, now)) throw new ReservationConflictException("该时段已停止预约：预约时距结束须至少30分钟。");
    }
    public LocalDateTime checkinDeadline(LocalDate date, TimeSlot slot, LocalDateTime now) {
        // DATETIME stores whole seconds. Never grant more than ten minutes by rounding up.
        return now.isAfter(slot.normalDeadline(date)) ? now.plusMinutes(10).withNano(0) : slot.normalDeadline(date);
    }
}
