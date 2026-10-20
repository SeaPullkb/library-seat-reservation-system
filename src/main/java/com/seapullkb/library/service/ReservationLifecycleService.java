package com.seapullkb.library.service;

import com.seapullkb.library.entity.Reservation;
import com.seapullkb.library.entity.TimeSlot;
import com.seapullkb.library.mapper.ReservationMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

@Service
public class ReservationLifecycleService {
    private final ReservationLocks locks;
    private final ReservationMapper records;
    private final ReservationTransactions transactions;
    private final Clock clock;
    public ReservationLifecycleService(ReservationLocks locks, ReservationMapper records,
                                         ReservationTransactions transactions, Clock clock) {
        this.locks = locks; this.records = records; this.transactions = transactions; this.clock = clock;
    }

    public LifecycleResult checkIn(String username, Long id) {
        LifecycleResult result = transactions.execute(() -> {
            var locked = locks.owned(username, id);
            Reservation record = locked.reservation();
            LocalDateTime now = LocalDateTime.now(clock);
            LifecycleResult settled = settle(record, now);
            if (settled != LifecycleResult.NONE) return settled;
            if (record.getStatus().equals("USING") && record.getCheckinAt() != null) return LifecycleResult.ALREADY_CHECKED_IN;
            if (!record.getStatus().equals("PENDING") || record.getCheckinAt() != null)
                throw new ReservationConflictException("此预约当前状态不能签到。");
            TimeSlot slot = TimeSlot.fromCode(record.getTimeSlot());
            if (now.isBefore(slot.checkinOpensOn(record.getReserveDate())) || now.isBefore(record.getCreatedAt()))
                throw new ReservationConflictException("尚未到签到时间，普通预约最早在时段开始前15分钟签到。");
            if (locked.room().getStatus() != 1 || locked.seat().getStatus() != 1)
                throw new ReservationConflictException("阅览室或座位已停用，无法签到，请联系管理员。");
            if (records.checkIn(id, locked.user().getId(), now) != 1)
                throw new ReservationConflictException("预约状态或签到时间已变化，请刷新后重试。");
            return LifecycleResult.CHECKED_IN;
        });
        // Expiration/completion must commit before the request reports a business error.
        if (result == LifecycleResult.EXPIRED) throw new ReservationConflictException("已超过签到截止时间，预约已超时释放，不能签到。");
        if (result == LifecycleResult.AUTO_COMPLETED) throw new ReservationConflictException("时段已结束，预约已自动完成，不能再次签到。");
        return result;
    }

    public LifecycleResult checkOut(String username, Long id) {
        LifecycleResult result = transactions.execute(() -> {
            var locked = locks.owned(username, id);
            Reservation record = locked.reservation();
            LocalDateTime now = LocalDateTime.now(clock);
            LifecycleResult settled = settle(record, now);
            if (settled != LifecycleResult.NONE) return settled;
            if (record.getStatus().equals("COMPLETED")) return LifecycleResult.ALREADY_COMPLETED;
            if (!record.getStatus().equals("USING") || record.getCheckinAt() == null)
                throw new ReservationConflictException("只有已签到且使用中的预约可以签退。");
            if (records.complete(id, now, now) != 1)
                throw new ReservationConflictException("预约状态已变化，请刷新后重试。");
            return LifecycleResult.CHECKED_OUT;
        });
        if (result == LifecycleResult.EXPIRED) throw new ReservationConflictException("预约已超时释放，不能签退。");
        return result;
    }

    public LifecycleResult processDue(Long id) {
        return transactions.execute(() -> {
            var locked = locks.system(id);
            return locked == null ? LifecycleResult.NONE : settle(locked.reservation(), LocalDateTime.now(clock));
        });
    }

    private LifecycleResult settle(Reservation record, LocalDateTime now) {
        LocalDateTime end = TimeSlot.fromCode(record.getTimeSlot()).endsOn(record.getReserveDate());
        if (record.getStatus().equals("PENDING") && record.getCheckinAt() == null
                && (now.isAfter(record.getCheckinDeadline()) || !now.isBefore(end))) {
            return records.expireOne(record.getId(), now) == 1 ? LifecycleResult.EXPIRED : LifecycleResult.NONE;
        }
        if (record.getStatus().equals("USING") && !now.isBefore(end)) {
            return records.complete(record.getId(), end, now) == 1 ? LifecycleResult.AUTO_COMPLETED : LifecycleResult.NONE;
        }
        return LifecycleResult.NONE;
    }
}
