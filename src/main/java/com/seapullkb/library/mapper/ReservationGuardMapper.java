package com.seapullkb.library.mapper;

import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** Read-only guards for maintenance, coordinated with reservation writes by room/seat locks. */
public interface ReservationGuardMapper {
    @Select("""
            SELECT COUNT(*) FROM reserve_record r JOIN seat s ON s.id = r.seat_id
            WHERE s.room_id = #{roomId} AND (#{seatId} IS NULL OR s.id = #{seatId})
              AND (r.status = 'USING' OR (r.status = 'PENDING' AND r.checkin_deadline >= #{now}))
              AND TIMESTAMP(r.reserve_date, CASE r.time_slot
                  WHEN 1 THEN '12:00:00' WHEN 2 THEN '18:00:00' ELSE '22:00:00' END) > #{now}
            """)
    long activeCount(@Param("roomId") Long roomId, @Param("seatId") Long seatId,
                     @Param("now") LocalDateTime now);

    @Select("SELECT COUNT(*) FROM reserve_record WHERE seat_id = #{seatId}")
    long historyCount(Long seatId);
}
