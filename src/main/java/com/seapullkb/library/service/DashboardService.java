package com.seapullkb.library.service;

import com.seapullkb.library.entity.TimeSlot;
import com.seapullkb.library.mapper.DashboardMapper;
import java.time.*;
import java.util.List;
import java.util.stream.IntStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {
    private final DashboardMapper mapper;
    private final ReservationService reservations;
    private final ReservationRules rules;
    private final Clock clock;
    public DashboardService(DashboardMapper mapper, ReservationService reservations, ReservationRules rules, Clock clock) {
        this.mapper=mapper; this.reservations=reservations; this.rules=rules; this.clock=clock;
    }

    @Transactional(readOnly=true)
    public StudentDashboard student(String username, LocalDate date, Integer slot) {
        // Live database role validation occurs even when there are no reservations.
        var active=reservations.active(username);
        return new StudentDashboard(catalog(date,slot),active);
    }

    @Transactional(readOnly=true)
    public Catalog catalog(LocalDate requestedDate, Integer requestedSlot) {
        LocalDateTime now=LocalDateTime.now(clock);
        LocalDate today=now.toLocalDate(), date=requestedDate==null?today:requestedDate;
        if(date.isBefore(today)||date.isAfter(today.plusDays(6)))
            throw new IllegalArgumentException("只能查看今天及未来6天的座位。");
        TimeSlot slot=requestedSlot==null?null:TimeSlot.fromCode(requestedSlot);
        if(slot==null) {
            for(var candidate:TimeSlot.values()) if(rules.isOpen(date,candidate,now)) { slot=candidate; break; }
            if(slot==null) {
                if(requestedDate==null) { date=today.plusDays(1); slot=TimeSlot.MORNING; }
                else slot=TimeSlot.EVENING;
            }
        }
        return new Catalog(date,slot,rules.isOpen(date,slot,now),mapper.availableRooms(date,slot.getCode(),now),
                IntStream.range(0,7).mapToObj(today::plusDays).toList());
    }

    public record Catalog(LocalDate date, TimeSlot slot, boolean bookingOpen, List<RoomAvailability> rooms,
                          List<LocalDate> dates) {
        public long getEnabledSeats() { return rooms.stream().mapToLong(RoomAvailability::enabledSeats).sum(); }
        public long getAvailableSeats() { return rooms.stream().mapToLong(RoomAvailability::availableSeats).sum(); }
    }
    public record StudentDashboard(Catalog catalog,List<ReservationSummary> active) {
        public ReservationSummary getNext() { return active.isEmpty()?null:active.get(0); }
    }
}
