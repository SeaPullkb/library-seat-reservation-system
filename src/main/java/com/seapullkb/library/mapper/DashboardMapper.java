package com.seapullkb.library.mapper;

import com.seapullkb.library.service.RoomAvailability;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.*;

public interface DashboardMapper {
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
}
