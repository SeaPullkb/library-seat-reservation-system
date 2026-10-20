package com.seapullkb.library.service;

import java.time.LocalDate;

public record DailyStatistics(LocalDate date,long reservations,long students,long eligible,long checkedIn,long expired) {
    public Double getCheckinRate() { return eligible==0?null:Math.round(checkedIn*10000.0/eligible)/100.0; }
}
