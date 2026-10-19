package com.seapullkb.library.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.seapullkb.library.entity.ReadingRoom;
import com.seapullkb.library.entity.Seat;
import com.seapullkb.library.entity.TimeSlot;
import com.seapullkb.library.mapper.ReadingRoomMapper;
import com.seapullkb.library.mapper.ReservationGuardMapper;
import com.seapullkb.library.mapper.SeatMapper;
import com.seapullkb.library.web.RoomForm;
import com.seapullkb.library.web.SeatForm;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SpaceService {
    private final ReadingRoomMapper rooms;
    private final SeatMapper seats;
    private final ReservationGuardMapper reservations;
    private final Clock clock;
    public SpaceService(ReadingRoomMapper rooms, SeatMapper seats, ReservationGuardMapper reservations, Clock clock) {
        this.rooms = rooms; this.seats = seats; this.reservations = reservations; this.clock = clock;
    }

    public List<RoomSummary> listRooms(boolean enabledOnly) { return rooms.summaries(enabledOnly); }

    public ReadingRoom room(Long roomId, boolean enabledOnly) {
        ReadingRoom room = rooms.selectById(roomId);
        if (room == null || (enabledOnly && room.getStatus() != 1))
            throw new ResourceNotFoundException("阅览室不存在或暂未开放。");
        return room;
    }

    public List<Seat> listSeats(Long roomId) {
        room(roomId, false);
        return seats.selectList(new LambdaQueryWrapper<Seat>().eq(Seat::getRoomId, roomId)
                .orderByAsc(Seat::getSeatNo).orderByAsc(Seat::getId));
    }

    public Seat seat(Long roomId, Long seatId) {
        room(roomId, false);
        Seat seat = seats.selectById(seatId);
        if (seat == null || !seat.getRoomId().equals(roomId)) throw new ResourceNotFoundException("该阅览室中没有此座位。");
        return seat;
    }

    @Transactional
    public Long saveRoom(Long roomId, RoomForm form) {
        ReadingRoom room;
        if (roomId == null) {
            room = new ReadingRoom(); room.setStatus(1); room.setCreatedAt(LocalDateTime.now(clock));
        } else room = lockRoom(roomId);
        room.setRoomName(form.name().strip()); room.setLocation(form.location().strip());
        room.setDescription(form.description() == null ? null : form.description().strip());
        if (roomId == null) rooms.insert(room); else rooms.updateById(room);
        return room.getId();
    }

    @Transactional
    public void setRoomEnabled(Long roomId, boolean enabled) {
        ReadingRoom room = lockRoom(roomId);
        if (!enabled && reservations.activeCount(roomId, null, LocalDateTime.now(clock)) > 0)
            throw new MaintenanceConflictException("此阅览室有未结束的有效预约，请先处理相关预约再停用。");
        room.setStatus(enabled ? 1 : 0); rooms.updateById(room);
    }

    @Transactional
    public Long saveSeat(Long roomId, Long seatId, SeatForm form) {
        lockRoom(roomId);
        Seat seat;
        if (seatId == null) {
            seat = new Seat(); seat.setRoomId(roomId); seat.setStatus(1); seat.setCreatedAt(LocalDateTime.now(clock));
        } else seat = lockSeat(roomId, seatId);
        seat.setSeatNo(form.seatNo().strip());
        try {
            if (seatId == null) seats.insert(seat); else seats.updateById(seat);
        } catch (DuplicateKeyException ex) {
            throw new MaintenanceConflictException("该阅览室内已存在此座位编号，请使用其他编号。");
        }
        return seat.getId();
    }

    @Transactional
    public void setSeatEnabled(Long roomId, Long seatId, boolean enabled) {
        lockRoom(roomId);
        Seat seat = lockSeat(roomId, seatId);
        if (!enabled && reservations.activeCount(roomId, seatId, LocalDateTime.now(clock)) > 0)
            throw new MaintenanceConflictException("此座位有未结束的有效预约，请先处理相关预约再停用。");
        seat.setStatus(enabled ? 1 : 0); seats.updateById(seat);
    }

    @Transactional
    public void deleteSeat(Long roomId, Long seatId) {
        lockRoom(roomId); lockSeat(roomId, seatId);
        if (reservations.historyCount(seatId) > 0)
            throw new MaintenanceConflictException("此座位有关联预约记录，不能删除；请使用停用以保留历史。");
        seats.deleteById(seatId);
    }

    @Transactional(readOnly = true)
    public List<SeatView> studentGrid(Long roomId, LocalDate date, int slotCode, Long userId) {
        room(roomId, true);
        TimeSlot.fromCode(slotCode);
        LocalDate today = LocalDate.now(clock);
        if (date.isBefore(today) || date.isAfter(today.plusDays(6)))
            throw new IllegalArgumentException("只能查看今天及未来6天的座位。");
        return seats.grid(roomId, date, slotCode, userId, LocalDateTime.now(clock));
    }

    private ReadingRoom lockRoom(Long id) {
        ReadingRoom room = rooms.lockById(id);
        if (room == null) throw new ResourceNotFoundException("阅览室不存在。");
        return room;
    }
    private Seat lockSeat(Long roomId, Long seatId) {
        Seat seat = seats.lockById(roomId, seatId);
        if (seat == null) throw new ResourceNotFoundException("该阅览室中没有此座位。");
        return seat;
    }
}
