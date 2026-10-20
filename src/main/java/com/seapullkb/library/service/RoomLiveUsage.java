package com.seapullkb.library.service;

/** Current use is distinct from a slot's historical locked occupancy. */
public record RoomLiveUsage(Long id, String name, Integer status, long enabledSeats, long usingSeats) { }
