package com.seapullkb.library.service;

import com.seapullkb.library.entity.ReservationStatus;
import com.seapullkb.library.entity.TimeSlot;
import java.time.LocalDate;

public record AdminReservationFilter(String keyword, Long roomId, LocalDate date, Integer slot, ReservationStatus status) {
    public AdminReservationFilter {
        keyword = keyword == null ? "" : keyword.strip();
        if (keyword.length() > 50 || (roomId != null && roomId <= 0)) throw new IllegalArgumentException("查询条件无效。");
        if (slot != null) TimeSlot.fromCode(slot);
    }
}
