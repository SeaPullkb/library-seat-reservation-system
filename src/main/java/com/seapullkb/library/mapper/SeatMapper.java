package com.seapullkb.library.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seapullkb.library.entity.Seat;
import com.seapullkb.library.service.SeatView;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface SeatMapper extends BaseMapper<Seat> {
    @Select("SELECT * FROM seat WHERE id = #{id} AND room_id = #{roomId} FOR UPDATE")
    Seat lockById(@Param("roomId") Long roomId, @Param("id") Long id);

    @Select("""
            SELECT s.id, s.seat_no, s.status,
              CASE WHEN s.status = 0 THEN 'DISABLED'
                   WHEN MAX(CASE WHEN r.user_id = #{userId} THEN 1 ELSE 0 END) = 1 THEN 'MINE'
                   WHEN COUNT(r.id) > 0 THEN 'OCCUPIED'
                   ELSE 'FREE' END AS availability
            FROM seat s
            LEFT JOIN reserve_record r ON r.seat_id = s.id
              AND r.reserve_date = #{date} AND r.time_slot = #{slot}
              AND (r.status IN ('USING', 'COMPLETED')
                   OR (r.status = 'PENDING' AND r.checkin_deadline >= #{now}))
            WHERE s.room_id = #{roomId}
            GROUP BY s.id, s.seat_no, s.status
            ORDER BY s.seat_no, s.id
            """)
    List<SeatView> grid(@Param("roomId") Long roomId, @Param("date") LocalDate date,
                        @Param("slot") int slot, @Param("userId") Long userId,
                        @Param("now") LocalDateTime now);
}
