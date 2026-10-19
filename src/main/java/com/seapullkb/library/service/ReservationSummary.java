package com.seapullkb.library.service;

import com.seapullkb.library.entity.ReservationStatus;
import com.seapullkb.library.entity.TimeSlot;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record ReservationSummary(Long id, String roomName, String seatNo, LocalDate reserveDate,
                                 Integer timeSlot, String status, LocalDateTime checkinDeadline,
                                 LocalDateTime checkinAt, LocalDateTime checkoutAt, LocalDateTime releasedAt, LocalDateTime createdAt,
                                 LocalDateTime cancelledAt, String cancelReason) {
    public String getStatusLabel() { return ReservationStatus.valueOf(status).getLabel(); }
    public String getSlotLabel() { return TimeSlot.fromCode(timeSlot).getLabel() + " " + TimeSlot.fromCode(timeSlot).getRange(); }
    public LocalDateTime getCheckinOpensAt() { return TimeSlot.fromCode(timeSlot).checkinOpensOn(reserveDate); }
    public boolean isLateBooking() { return checkinDeadline.isAfter(TimeSlot.fromCode(timeSlot).normalDeadline(reserveDate)); }
    public boolean canCheckIn(LocalDateTime now) {
        return status.equals("PENDING") && checkinAt == null && !now.isBefore(getCheckinOpensAt())
                && !now.isBefore(createdAt) && !now.isAfter(checkinDeadline)
                && now.isBefore(TimeSlot.fromCode(timeSlot).endsOn(reserveDate));
    }
    public boolean canCheckOut(LocalDateTime now) {
        return status.equals("USING") && checkinAt != null && now.isBefore(TimeSlot.fromCode(timeSlot).endsOn(reserveDate));
    }
    public boolean canCancel(LocalDateTime now) {
        return status.equals("PENDING") && checkinAt == null && !now.isAfter(checkinDeadline)
                && now.isBefore(TimeSlot.fromCode(timeSlot).startsOn(reserveDate));
    }
}
