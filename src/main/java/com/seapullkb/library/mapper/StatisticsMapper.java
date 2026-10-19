package com.seapullkb.library.mapper;

import com.seapullkb.library.service.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.*;

public interface StatisticsMapper {
    @Select("""
            SELECT #{date} AS date,COUNT(*) AS reservations,COUNT(DISTINCT CASE WHEN status<>'CANCELLED' THEN user_id END) AS students,
              COALESCE(SUM(status<>'CANCELLED' AND checkin_deadline < #{now}),0) AS eligible,
              COALESCE(SUM(status<>'CANCELLED' AND checkin_deadline < #{now} AND checkin_at IS NOT NULL),0) AS checked_in,
              COALESCE(SUM(status='EXPIRED'),0) AS expired
            FROM reserve_record WHERE reserve_date=#{date}
            """)
    DailyStatistics daily(@Param("date") LocalDate date,@Param("now") LocalDateTime now);
    @Select("""
            SELECT reserve_date AS date,COUNT(*) AS reservations,
              COUNT(DISTINCT CASE WHEN status<>'CANCELLED' THEN user_id END) AS students,
              0 AS eligible,0 AS checked_in,0 AS expired FROM reserve_record
            WHERE reserve_date BETWEEN #{start} AND #{end} GROUP BY reserve_date ORDER BY reserve_date
            """)
    List<DailyStatistics> trend(@Param("start") LocalDate start,@Param("end") LocalDate end);
    @Select("""
            SELECT rr.id,rr.room_name AS name,rr.status,
              (SELECT COUNT(*) FROM seat s WHERE s.room_id=rr.id AND s.status=1) AS enabled_seats,
              (SELECT COUNT(DISTINCT s.id) FROM seat s JOIN reserve_record r ON r.seat_id=s.id
               WHERE s.room_id=rr.id AND r.reserve_date=#{date} AND r.time_slot=#{slot}
                 AND (r.status IN ('USING','COMPLETED') OR (r.status='PENDING' AND r.checkin_deadline>=#{now}))) AS occupied_seats
            FROM reading_room rr ORDER BY rr.id
            """)
    List<RoomStatistics> rooms(@Param("date") LocalDate date,@Param("slot") int slot,@Param("now") LocalDateTime now);
}
