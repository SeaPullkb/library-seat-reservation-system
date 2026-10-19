package com.seapullkb.library.service;

import com.seapullkb.library.entity.*;
import com.seapullkb.library.mapper.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/** Call only inside a ReservationTransactions operation. */
@Component
public class ReservationLocks {
    private final UserMapper users;
    private final ReadingRoomMapper rooms;
    private final SeatMapper seats;
    private final ReservationMapper reservations;
    public ReservationLocks(UserMapper users, ReadingRoomMapper rooms, SeatMapper seats, ReservationMapper reservations) {
        this.users = users; this.rooms = rooms; this.seats = seats; this.reservations = reservations;
    }
    public UserAccount student(String username) {
        UserAccount user = users.lockByUsername(username);
        if (user == null || user.getStatus() != 1 || !user.getRole().equals(Role.STUDENT.name()))
            throw new AccessDeniedException("仅启用的学生账号可以操作自己的预约。");
        return user;
    }
    public ReadingRoom room(Long id) {
        ReadingRoom room = rooms.lockById(id);
        if (room == null) throw new ResourceNotFoundException("阅览室不存在。");
        return room;
    }
    public Seat seat(Long roomId, Long id) {
        Seat seat = seats.lockById(roomId, id);
        if (seat == null) throw new ResourceNotFoundException("该阅览室中没有此座位。");
        return seat;
    }
    public Locked owned(String username, Long id) { return forUser(student(username), id); }

    public Locked system(Long id) {
        Reservation reference = reservations.selectById(id);
        if (reference == null) return null;
        UserAccount user = users.lockById(reference.getUserId());
        if (user == null) return null;
        return forUser(user, id);
    }

    private Locked forUser(UserAccount user, Long id) {
        Reservation reference = reservations.findOwned(id, user.getId());
        if (reference == null) throw new ResourceNotFoundException("预约记录不存在。");
        Seat referenceSeat = seats.selectById(reference.getSeatId());
        if (referenceSeat == null) throw new ResourceNotFoundException("预约关联座位不存在。");
        ReadingRoom room = room(referenceSeat.getRoomId());
        Seat seat = seat(room.getId(), referenceSeat.getId());
        Reservation current = reservations.lockOwned(id, user.getId());
        if (current == null) throw new ResourceNotFoundException("预约记录不存在。");
        return new Locked(user, room, seat, current);
    }

    public record Locked(UserAccount user, ReadingRoom room, Seat seat, Reservation reservation) { }
}
