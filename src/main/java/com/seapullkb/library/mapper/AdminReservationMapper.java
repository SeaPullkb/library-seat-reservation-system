package com.seapullkb.library.mapper;

import com.seapullkb.library.service.AdminReservationFilter;
import com.seapullkb.library.service.AdminReservationRow;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.*;

public interface AdminReservationMapper {
    String JOINS = " FROM reserve_record r JOIN `user` u ON u.id=r.user_id JOIN seat s ON s.id=r.seat_id JOIN reading_room rr ON rr.id=s.room_id LEFT JOIN `user` actor ON actor.id=r.cancelled_by ";
    String FILTER = " WHERE (#{filter.keyword} = '' OR LOCATE(#{filter.keyword},u.username)>0 OR LOCATE(#{filter.keyword},u.real_name)>0) "
            + "AND (#{filter.roomId} IS NULL OR rr.id=#{filter.roomId}) AND (#{filter.date} IS NULL OR r.reserve_date=#{filter.date}) "
            + "AND (#{filter.slot} IS NULL OR r.time_slot=#{filter.slot}) AND (#{filter.status} IS NULL OR "
            + ReservationMapper.EFFECTIVE_STATUS + " = #{filter.status}) ";
    @Select("SELECT r.id,u.username,u.real_name,rr.room_name,s.seat_no,r.reserve_date,r.time_slot,"
            + ReservationMapper.EFFECTIVE_STATUS + " AS status,r.checkin_deadline,r.checkin_at,r.checkout_at,r.cancelled_at,actor.username AS cancelled_by,r.cancel_reason"
            + JOINS + FILTER + " ORDER BY r.created_at DESC,r.id DESC LIMIT 20 OFFSET #{offset}")
    List<AdminReservationRow> search(@Param("filter") AdminReservationFilter filter, @Param("now") LocalDateTime now, @Param("offset") long offset);
    @Select("SELECT COUNT(*)" + JOINS + FILTER)
    long count(@Param("filter") AdminReservationFilter filter, @Param("now") LocalDateTime now);
    @Update("""
            UPDATE reserve_record SET status='CANCELLED',cancelled_by=#{actorId},cancel_reason=#{reason},
              cancelled_at=#{now},updated_at=#{now}
            WHERE id=#{id} AND status='PENDING' AND checkin_at IS NULL AND checkin_deadline >= #{now}
            """)
    int cancel(@Param("id") Long id, @Param("actorId") Long actorId, @Param("reason") String reason, @Param("now") LocalDateTime now);
}
