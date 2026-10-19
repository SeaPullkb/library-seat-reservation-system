package com.seapullkb.library.mapper;

import com.seapullkb.library.service.RoomAvailability;
import com.seapullkb.library.service.AdminDashboardMetrics;
import com.seapullkb.library.service.AdminReservationRow;
import com.seapullkb.library.service.RoomLiveUsage;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.*;

public interface DashboardMapper {
    String CURRENT_USING="r.status='USING' AND r.checkin_at<=#{now} AND " + ReservationMapper.SLOT_END + ">#{now}";
    String CURRENT_PENDING="r.status='PENDING' AND r.checkin_at IS NULL AND r.created_at<=#{now} AND r.checkin_deadline>=#{now} AND "
            + ReservationMapper.SLOT_END + ">#{now} AND TIMESTAMP(r.reserve_date, CASE r.time_slot WHEN 1 THEN '08:00:00' WHEN 2 THEN '14:00:00' ELSE '19:00:00' END) - INTERVAL 15 MINUTE<=#{now}";
    String ADMIN_COLUMNS="r.id,u.username,u.real_name,rr.room_name,s.seat_no,r.reserve_date,r.time_slot,"
            + ReservationMapper.EFFECTIVE_STATUS + " AS status,r.checkin_deadline,r.checkin_at,r.checkout_at,r.cancelled_at,actor.username AS cancelled_by,r.cancel_reason";
    @Select("""
            SELECT rr.id, rr.room_name AS name, rr.location, rr.description,
              (SELECT COUNT(*) FROM seat s WHERE s.room_id=rr.id AND s.status=1) AS enabled_seats,
              (SELECT COUNT(*) FROM seat s WHERE s.room_id=rr.id AND s.status=1
                AND NOT EXISTS (SELECT 1 FROM reserve_record r WHERE r.seat_id=s.id
                  AND r.reserve_date=#{date} AND r.time_slot=#{slot}
                  AND (r.status IN ('USING','COMPLETED')
                    OR (r.status='PENDING' AND r.checkin_deadline>=#{now})))) AS available_seats
            FROM reading_room rr WHERE rr.status=1 ORDER BY rr.id
            """)
    List<RoomAvailability> availableRooms(@Param("date") LocalDate date, @Param("slot") int slot,
                                         @Param("now") LocalDateTime now);

    @Select("SELECT COUNT(DISTINCT CASE WHEN r.reserve_date=DATE(#{now}) AND r.status<>'CANCELLED' THEN r.user_id END) AS today_students,"
            + "COUNT(DISTINCT CASE WHEN " + CURRENT_USING + " THEN r.seat_id END) AS using_seats,"
            + "COALESCE(SUM(" + CURRENT_PENDING + "),0) AS pending,"
            + "COALESCE(SUM(r.status='EXPIRED' AND r.released_at>=DATE(#{now}) AND r.released_at<DATE(#{now})+INTERVAL 1 DAY),0) AS today_expired FROM reserve_record r")
    AdminDashboardMetrics metrics(@Param("now") LocalDateTime now);

    @Select("SELECT " + ADMIN_COLUMNS + AdminReservationMapper.JOINS + " ORDER BY r.created_at DESC,r.id DESC LIMIT 6")
    List<AdminReservationRow> recent(@Param("now") LocalDateTime now);

    @Select("SELECT " + ADMIN_COLUMNS + AdminReservationMapper.JOINS + " WHERE " + CURRENT_PENDING + " ORDER BY r.checkin_deadline,r.id LIMIT 6")
    List<AdminReservationRow> attention(@Param("now") LocalDateTime now);

    @Select("SELECT rr.id,rr.room_name AS name,rr.status,"
            + "(SELECT COUNT(*) FROM seat s WHERE s.room_id=rr.id AND s.status=1) AS enabled_seats,"
            + "(SELECT COUNT(DISTINCT r.seat_id) FROM reserve_record r JOIN seat s ON s.id=r.seat_id WHERE s.room_id=rr.id AND "
            + CURRENT_USING + ") AS using_seats FROM reading_room rr ORDER BY rr.id")
    List<RoomLiveUsage> liveRooms(@Param("now") LocalDateTime now);
}
