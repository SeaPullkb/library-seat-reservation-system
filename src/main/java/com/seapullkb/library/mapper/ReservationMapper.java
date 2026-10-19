package com.seapullkb.library.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seapullkb.library.entity.Reservation;
import com.seapullkb.library.service.ReservationSummary;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface ReservationMapper extends BaseMapper<Reservation> {
    @Select("""
            SELECT COUNT(*) FROM reserve_record WHERE user_id = #{userId}
              AND reserve_date = #{date} AND time_slot = #{slot}
              AND (status IN ('USING','COMPLETED') OR (status = 'PENDING' AND checkin_deadline >= #{now}))
            """)
    long studentConflict(@Param("userId") Long userId, @Param("date") LocalDate date,
                         @Param("slot") int slot, @Param("now") LocalDateTime now);

    @Select("""
            SELECT COUNT(*) FROM reserve_record WHERE seat_id = #{seatId}
              AND reserve_date = #{date} AND time_slot = #{slot}
              AND (status IN ('USING','COMPLETED') OR (status = 'PENDING' AND checkin_deadline >= #{now}))
            """)
    long seatConflict(@Param("seatId") Long seatId, @Param("date") LocalDate date,
                      @Param("slot") int slot, @Param("now") LocalDateTime now);

    // Only expire this locked seat's records. Do not lock other seats or users out of order.
    @Update("""
            UPDATE reserve_record SET status = 'EXPIRED', released_at = #{now}, updated_at = #{now}
            WHERE seat_id = #{seatId} AND reserve_date = #{date} AND time_slot = #{slot}
              AND status = 'PENDING' AND checkin_at IS NULL AND checkin_deadline < #{now}
            """)
    int expireTarget(@Param("seatId") Long seatId, @Param("date") LocalDate date,
                     @Param("slot") int slot, @Param("now") LocalDateTime now);

    @Select("SELECT * FROM reserve_record WHERE id = #{id} AND user_id = #{userId}")
    Reservation findOwned(@Param("id") Long id, @Param("userId") Long userId);

    @Select("SELECT * FROM reserve_record WHERE id = #{id} AND user_id = #{userId} FOR UPDATE")
    Reservation lockOwned(@Param("id") Long id, @Param("userId") Long userId);

    @Update("""
            UPDATE reserve_record SET status = 'CANCELLED', cancelled_at = #{now}, cancelled_by = #{userId},
              cancel_reason = '学生主动取消', updated_at = #{now}
            WHERE id = #{id} AND user_id = #{userId} AND status = 'PENDING' AND checkin_at IS NULL
              AND checkin_deadline >= #{now}
            """)
    int cancelPending(@Param("id") Long id, @Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Select("""
            SELECT r.id, rr.room_name, s.seat_no, r.reserve_date, r.time_slot,
              CASE WHEN r.status = 'PENDING' AND r.checkin_deadline < #{now} THEN 'EXPIRED' ELSE r.status END AS status,
              r.checkin_deadline, r.checkin_at, r.created_at, r.cancelled_at, r.cancel_reason
            FROM reserve_record r JOIN seat s ON s.id = r.seat_id JOIN reading_room rr ON rr.id = s.room_id
            WHERE r.user_id = #{userId} AND (#{status} IS NULL OR
              CASE WHEN r.status = 'PENDING' AND r.checkin_deadline < #{now} THEN 'EXPIRED' ELSE r.status END = #{status})
            ORDER BY r.created_at DESC, r.id DESC LIMIT #{limit} OFFSET #{offset}
            """)
    List<ReservationSummary> mine(@Param("userId") Long userId, @Param("status") String status,
                                  @Param("now") LocalDateTime now, @Param("limit") int limit, @Param("offset") long offset);

    @Select("""
            SELECT COUNT(*) FROM reserve_record r WHERE r.user_id = #{userId} AND (#{status} IS NULL OR
              CASE WHEN r.status = 'PENDING' AND r.checkin_deadline < #{now} THEN 'EXPIRED' ELSE r.status END = #{status})
            """)
    long mineCount(@Param("userId") Long userId, @Param("status") String status, @Param("now") LocalDateTime now);
}
