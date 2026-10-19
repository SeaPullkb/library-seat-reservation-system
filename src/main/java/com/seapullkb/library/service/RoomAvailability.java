package com.seapullkb.library.service;

/** Availability for one date/slot; completed use still locks its original seat. */
public record RoomAvailability(Long id, String name, String location, String description,
                               long enabledSeats, long availableSeats) { }
