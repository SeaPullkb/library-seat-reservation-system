package com.seapullkb.library.service;

public record SeatView(Long id, String seatNo, Integer status, String availability) {
    public String getLabel() {
        return switch (availability) {
            case "DISABLED" -> "已停用";
            case "MINE" -> "我的预约";
            case "OCCUPIED" -> "已占用";
            default -> "空闲";
        };
    }
}
