package com.seapullkb.library.service;

import java.time.LocalDate;

public final class QueryDates {
    private QueryDates() { }
    public static void requireSupported(LocalDate date) {
        if(date!=null && (date.getYear()<1000 || date.getYear()>9999)) throw new IllegalArgumentException("日期须在1000至9999年之间。");
    }
}
