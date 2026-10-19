package com.seapullkb.library.service;

import com.seapullkb.library.entity.ReservationStatus;
import com.seapullkb.library.entity.TimeSlot;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record ReservationSummary(Long id, String roomName, String seatNo, LocalDate reserveDate,
                                 Integer timeSlot, String status, LocalDateTime checkinDeadline,
                                 LocalDateTime checkinAt, LocalDateTime createdAt,
                                 LocalDateTime cancelledAt, String cancelReason) {
    public String getStatusLabel() { return ReservationStatus.valueOf(status).getLabel(); }
    public String getSlotLabel() { return TimeSlot.fromCode(timeSlot).getLabel() + " " + TimeSlot.fromCode(timeSlot).getRange(); }
    public boolean canCancel(LocalDateTime now) {
        return status.equals("PENDING") && checkinAt == null && !now.isAfter(checkinDeadline)
                && now.isBefore(TimeSlot.fromCode(timeSlot).startsOn(reserveDate));
    }
}
