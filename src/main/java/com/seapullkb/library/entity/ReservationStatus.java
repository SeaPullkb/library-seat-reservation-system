package com.seapullkb.library.entity;

public enum ReservationStatus {
    PENDING("待签到"), USING("使用中"), COMPLETED("已完成"), CANCELLED("已取消"), EXPIRED("超时释放");
    private final String label;
    ReservationStatus(String label) { this.label = label; }
    public String getLabel() { return label; }
}
