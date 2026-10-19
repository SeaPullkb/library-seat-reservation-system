package com.seapullkb.library.web;

import com.seapullkb.library.entity.TimeSlot;
import com.seapullkb.library.service.StatisticsService;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/admin/statistics")
public class StatisticsController {
    private final StatisticsService statistics;
    private final Clock clock;
    public StatisticsController(StatisticsService statistics,Clock clock) { this.statistics=statistics;this.clock=clock; }
    @GetMapping
    public String statistics(Authentication auth,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue="1") int slot,Model model) {
        LocalDate selected=date==null?LocalDate.now(clock):date;
        model.addAttribute("dashboard",statistics.dashboard(auth.getName(),selected,slot));
        model.addAttribute("date",selected); model.addAttribute("slot",slot); model.addAttribute("slots",TimeSlot.values());
        model.addAttribute("title","数据统计"); return "admin/statistics";
    }
}
