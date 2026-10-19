package com.seapullkb.library.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;

public enum TimeSlot {
    MORNING(1, "上午", LocalTime.of(8, 0), LocalTime.of(12, 0)),
    AFTERNOON(2, "下午", LocalTime.of(14, 0), LocalTime.of(18, 0)),
    EVENING(3, "晚上", LocalTime.of(19, 0), LocalTime.of(22, 0));

    private final int code;
    private final String label;
    private final LocalTime start;
    private final LocalTime end;
    TimeSlot(int code, String label, LocalTime start, LocalTime end) {
        this.code = code; this.label = label; this.start = start; this.end = end;
    }
    public int getCode() { return code; }
    public String getLabel() { return label; }
    public String getRange() { return start + "—" + end; }
    public LocalDateTime endsOn(LocalDate date) { return date.atTime(end); }
    public static TimeSlot fromCode(int code) {
        return Arrays.stream(values()).filter(slot -> slot.code == code).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("请选择上午、下午或晚上时段。"));
    }
}
