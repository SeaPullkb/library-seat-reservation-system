package com.seapullkb.library.service;

import com.seapullkb.library.entity.*;
import com.seapullkb.library.mapper.*;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationService {
    private final ReservationLocks locks;
    private final ReservationMapper reservations;
    private final AccountService accounts;
    private final ReservationRules rules;
    private final Clock clock;
    private final ReservationTransactions transactions;
    public ReservationService(ReservationLocks locks, ReservationMapper reservations, AccountService accounts, ReservationRules rules, Clock clock,
                               ReservationTransactions transactions) {
        this.locks = locks; this.reservations = reservations;
        this.accounts = accounts; this.rules = rules; this.clock = clock;
        this.transactions = transactions;
    }

    public Long create(String username, Long roomId, Long seatId, LocalDate date, int slotCode) {
        return transactions.execute(() -> createInTransaction(username, roomId, seatId, date, slotCode));
    }

    private Long createInTransaction(String username, Long roomId, Long seatId, LocalDate date, int slotCode) {
        // All writers use user -> room -> seat -> reservation order. Read committed
        // ensures conflict checks see the preceding transaction after waiting for its locks.
        UserAccount user = locks.student(username);
        ReadingRoom room = locks.room(roomId);
        Seat seat = locks.seat(roomId, seatId);
        LocalDateTime now = LocalDateTime.now(clock);
        TimeSlot slot = TimeSlot.fromCode(slotCode);
        rules.requireOpen(date, slot, now);
        if (room.getStatus() != 1 || seat.getStatus() != 1)
            throw new ReservationConflictException("阅览室或座位已停用，请重新选择。");
        if (reservations.studentConflict(user.getId(), date, slotCode, now) > 0)
            throw new ReservationConflictException("你在此日期和时段已有预约或使用记录，不能重复预约。");
        if (reservations.seatConflict(seatId, date, slotCode, now) > 0)
            throw new ReservationConflictException("此座位已被占用，请选择其他座位。");
        reservations.expireTarget(seatId, date, slotCode, now);
        Reservation reservation = new Reservation();
        reservation.setUserId(user.getId()); reservation.setSeatId(seatId); reservation.setReserveDate(date);
        reservation.setTimeSlot(slotCode); reservation.setStatus(ReservationStatus.PENDING.name());
        reservation.setCheckinDeadline(rules.checkinDeadline(date, slot, now));
        reservation.setCreatedAt(now.withNano(0)); reservation.setUpdatedAt(now.withNano(0));
        reservations.insert(reservation);
        return reservation.getId();
    }

    public boolean cancel(String username, Long reservationId) {
        return transactions.execute(() -> cancelInTransaction(username, reservationId));
    }

    private boolean cancelInTransaction(String username, Long reservationId) {
        var locked = locks.owned(username, reservationId);
        UserAccount user = locked.user();
        Reservation reservation = locked.reservation();
        if (reservation.getStatus().equals("CANCELLED")) return false;
        LocalDateTime now = LocalDateTime.now(clock);
        if (!reservation.getStatus().equals("PENDING") || reservation.getCheckinAt() != null
                || now.isAfter(reservation.getCheckinDeadline())
                || !now.isBefore(TimeSlot.fromCode(reservation.getTimeSlot()).startsOn(reservation.getReserveDate())))
            throw new ReservationConflictException("仅可在时段开始前取消尚未签到的有效预约。");
        if (reservations.cancelPending(reservationId, user.getId(), now) != 1)
            throw new ReservationConflictException("预约状态已变化，请刷新后重试。");
        return true;
    }

    public Long studentId(String username) { return requireStudent(accounts.findByUsername(username)).getId(); }

    public boolean hasStudentConflict(Long userId, LocalDate date, int slot) {
        return reservations.studentConflict(userId, date, slot, LocalDateTime.now(clock)) > 0;
    }

    @Transactional(readOnly = true)
    public ReservationPage mine(String username, ReservationStatus status, int page) {
        if (page < 0 || page > 1_000_000) throw new IllegalArgumentException("页码无效。");
        Long userId = studentId(username);
        LocalDateTime now = LocalDateTime.now(clock);
        String filter = status == null ? null : status.name();
        return new ReservationPage(reservations.mine(userId, filter, now, 20, (long) page * 20),
                reservations.mineCount(userId, filter, now), page, 20);
    }

    private UserAccount requireStudent(UserAccount user) {
        if (user == null || user.getStatus() != 1 || !user.getRole().equals(Role.STUDENT.name()))
            throw new AccessDeniedException("仅启用的学生账号可以操作自己的预约。");
        return user;
    }
}
