package com.seapullkb.library.service;

import java.util.List;

public record ReservationPage(List<ReservationSummary> records, long total, int page, int pageSize) {
    public long getPages() { return (total + pageSize - 1) / pageSize; }
    public boolean hasNext() { return (long) (page + 1) * pageSize < total; }
}
