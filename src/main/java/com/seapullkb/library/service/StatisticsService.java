package com.seapullkb.library.service;

import com.seapullkb.library.entity.TimeSlot;
import com.seapullkb.library.mapper.StatisticsMapper;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StatisticsService {
    private final StatisticsMapper statistics;
    private final AdminAccess access;
    private final Clock clock;
    public StatisticsService(StatisticsMapper statistics,AdminAccess access,Clock clock) { this.statistics=statistics;this.access=access;this.clock=clock; }
    @Transactional(readOnly=true)
    public Dashboard dashboard(String actor,LocalDate date,int slot) {
        access.require(actor,false); TimeSlot.fromCode(slot);
        LocalDateTime now=LocalDateTime.now(clock); LocalDate today=now.toLocalDate();
        var byDate=statistics.trend(today.minusDays(6),today).stream().collect(Collectors.toMap(DailyStatistics::date,Function.identity()));
        List<DailyStatistics> trend=new ArrayList<>();
        for(int i=6;i>=0;i--) { LocalDate day=today.minusDays(i); trend.add(byDate.getOrDefault(day,new DailyStatistics(day,0,0,0,0,0))); }
        return new Dashboard(statistics.daily(date,now),statistics.rooms(date,slot,now),List.copyOf(trend));
    }
    public record Dashboard(DailyStatistics daily,List<RoomStatistics> rooms,List<DailyStatistics> trend) { }
}
