package com.seapullkb.library.service;

import com.seapullkb.library.entity.TimeSlot;
import com.seapullkb.library.mapper.AdminReservationMapper;
import com.seapullkb.library.mapper.ReservationMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminReservationService {
    private final AdminAccess access;
    private final ReservationLocks locks;
    private final ReservationMapper records;
    private final AdminReservationMapper adminRecords;
    private final ReservationTransactions transactions;
    private final Clock clock;
    public AdminReservationService(AdminAccess access, ReservationLocks locks, ReservationMapper records,
            AdminReservationMapper adminRecords, ReservationTransactions transactions, Clock clock) {
        this.access=access; this.locks=locks; this.records=records; this.adminRecords=adminRecords; this.transactions=transactions; this.clock=clock;
    }
    @Transactional(readOnly=true)
    public AdminPage<AdminReservationRow> search(String username, AdminReservationFilter filter, int page) {
        access.require(username,false); AdminPage.requirePage(page);
        LocalDateTime now=LocalDateTime.now(clock);
        return new AdminPage<>(adminRecords.search(filter,now,(long)page*20), adminRecords.count(filter,now),page,20);
    }
    public LifecycleResult cancel(String username, Long id, String reason) {
        if (reason == null || reason.isBlank() || reason.strip().length()>255)
            throw new IllegalArgumentException("请填写1–255字的取消原因。");
        LifecycleResult result=transactions.execute(() -> {
            var reference=records.selectById(id);
            if (reference==null) throw new ResourceNotFoundException("预约记录不存在。");
            var actor=access.lockActorAndTarget(username,reference.getUserId(),false);
            var locked=locks.system(id);
            if (locked==null) throw new ResourceNotFoundException("预约记录不存在。");
            var record=locked.reservation();
            LocalDateTime now=LocalDateTime.now(clock);
            if (record.getStatus().equals("CANCELLED")) return LifecycleResult.NONE;
            if (record.getStatus().equals("PENDING") && record.getCheckinAt()==null
                    && (now.isAfter(record.getCheckinDeadline()) || !now.isBefore(TimeSlot.fromCode(record.getTimeSlot()).endsOn(record.getReserveDate())))) {
                records.expireOne(id,now); return LifecycleResult.EXPIRED;
            }
            if (!record.getStatus().equals("PENDING") || record.getCheckinAt()!=null)
                throw new ReservationConflictException("仅可取消尚未签到的有效预约；已签到或终态记录保留。");
            if (adminRecords.cancel(id,actor.getId(),reason.strip(),now)!=1)
                throw new ReservationConflictException("预约状态已变化，请刷新后重试。");
            return LifecycleResult.CANCELLED;
        });
        if (result==LifecycleResult.EXPIRED) throw new ReservationConflictException("预约已经超时释放，未执行违规取消。");
        return result;
    }
}
