package com.seapullkb.library.service;

public record RoomSummary(Long id, String name, String location, String description,
                          Integer status, Long enabledSeats, Long totalSeats) {
}
