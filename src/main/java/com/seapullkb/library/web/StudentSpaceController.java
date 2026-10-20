package com.seapullkb.library.web;

import com.seapullkb.library.entity.TimeSlot;
import com.seapullkb.library.service.ReservationRules;
import com.seapullkb.library.service.ReservationService;
import com.seapullkb.library.service.SpaceService;
import com.seapullkb.library.service.DashboardService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.stream.IntStream;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/student/rooms")
public class StudentSpaceController {
    private final SpaceService spaces;
    private final ReservationService reservations;
    private final ReservationRules rules;
    private final Clock clock;
    private final DashboardService dashboards;
    public StudentSpaceController(SpaceService spaces, ReservationService reservations, ReservationRules rules, Clock clock, DashboardService dashboards) {
        this.spaces = spaces; this.reservations = reservations; this.rules = rules; this.clock = clock; this.dashboards=dashboards;
    }
    @GetMapping
    public String rooms(Model model,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required=false) Integer slot) {
        var catalog=dashboards.catalog(date,slot);
        model.addAttribute("title", "阅览室"); model.addAttribute("rooms", catalog.rooms());
        model.addAttribute("catalog",catalog); model.addAttribute("slots",TimeSlot.values());
        return "student/rooms";
    }
    @GetMapping("/{id}/seats")
    public String seats(@PathVariable Long id,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                         @RequestParam(defaultValue = "1") int slot,
                         @RequestParam(defaultValue = "false") boolean availableOnly,
                         Authentication authentication, Model model) {
        LocalDate today = LocalDate.now(clock);
        LocalDate selected = date == null ? today : date;
        Long userId = reservations.studentId(authentication.getName());
        var grid = spaces.studentGrid(id, selected, slot, userId);
        model.addAttribute("seatCounts", java.util.Map.of(
                "FREE",grid.stream().filter(s -> s.availability().equals("FREE")).count(),
                "OCCUPIED",grid.stream().filter(s -> s.availability().equals("OCCUPIED")).count(),
                "MINE",grid.stream().filter(s -> s.availability().equals("MINE")).count(),
                "DISABLED",grid.stream().filter(s -> s.availability().equals("DISABLED")).count()));
        model.addAttribute("title", "座位预约"); model.addAttribute("room", spaces.room(id, true));
        model.addAttribute("seats", availableOnly ? grid.stream().filter(seat -> seat.availability().equals("FREE")).toList() : grid);
        model.addAttribute("availableOnly", availableOnly);
        boolean open = rules.isOpen(selected, TimeSlot.fromCode(slot), LocalDateTime.now(clock));
        boolean conflict = reservations.hasStudentConflict(userId, selected, slot);
        model.addAttribute("bookingOpen", open && !conflict);
        model.addAttribute("closedReason", !open ? "该时段已停止预约：距结束不足30分钟。" : conflict ? "你在此日期时段已有预约或使用记录，请先查看我的预约。" : null);
        model.addAttribute("dates", IntStream.range(0, 7).mapToObj(today::plusDays).toList());
        model.addAttribute("date", selected); model.addAttribute("slot", slot); model.addAttribute("slots", TimeSlot.values());
        model.addAttribute("selectedSlot", TimeSlot.fromCode(slot));
        return "student/seats";
    }
}
