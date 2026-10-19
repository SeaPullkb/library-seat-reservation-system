package com.seapullkb.library.service;

public record RoomStatistics(Long id,String name,Integer status,long enabledSeats,long occupiedSeats) {
    public Double getReservationRate() { return enabledSeats==0?null:Math.round(occupiedSeats*10000.0/enabledSeats)/100.0; }
}
