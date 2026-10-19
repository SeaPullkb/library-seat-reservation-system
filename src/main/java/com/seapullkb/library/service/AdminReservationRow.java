package com.seapullkb.library.service;

import com.seapullkb.library.entity.ReservationStatus;
import com.seapullkb.library.entity.TimeSlot;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record AdminReservationRow(Long id, String username, String realName, String roomName, String seatNo,
        LocalDate reserveDate, Integer timeSlot, String status, LocalDateTime checkinDeadline,
        LocalDateTime checkinAt, LocalDateTime checkoutAt, LocalDateTime cancelledAt, String cancelledBy, String cancelReason) {
    public String getStatusLabel() { return ReservationStatus.valueOf(status).getLabel(); }
    public String getSlotLabel() { return TimeSlot.fromCode(timeSlot).getLabel() + " " + TimeSlot.fromCode(timeSlot).getRange(); }
    public boolean isCancellable() { return status.equals("PENDING") && checkinAt == null; }
}
