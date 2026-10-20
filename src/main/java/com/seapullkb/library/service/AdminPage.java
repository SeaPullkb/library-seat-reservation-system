package com.seapullkb.library.service;

import java.util.List;

public record AdminPage<T>(List<T> records, long total, int page, int pageSize) {
    public long getPages() { return (total + pageSize - 1) / pageSize; }
    public boolean hasNext() { return (long) (page + 1) * pageSize < total; }
    public static void requirePage(int page) {
        if (page < 0 || page > 1_000_000) throw new IllegalArgumentException("页码无效。");
    }
}
