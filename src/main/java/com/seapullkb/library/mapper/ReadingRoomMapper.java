package com.seapullkb.library.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seapullkb.library.entity.ReadingRoom;
import com.seapullkb.library.service.RoomSummary;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ReadingRoomMapper extends BaseMapper<ReadingRoom> {
    @Select("SELECT * FROM reading_room WHERE id = #{id} FOR UPDATE")
    ReadingRoom lockById(Long id);

    @Select("""
            SELECT r.id, r.room_name AS name, r.location, r.description, r.status,
              (SELECT COUNT(*) FROM seat s WHERE s.room_id = r.id AND s.status = 1) AS enabled_seats,
              (SELECT COUNT(*) FROM seat s WHERE s.room_id = r.id) AS total_seats
            FROM reading_room r WHERE (#{enabledOnly} = false OR r.status = 1)
            ORDER BY r.id
            """)
    List<RoomSummary> summaries(@Param("enabledOnly") boolean enabledOnly);
}
