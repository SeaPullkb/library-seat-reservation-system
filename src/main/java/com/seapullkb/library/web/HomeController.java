package com.seapullkb.library.web;

import com.seapullkb.library.service.AccountService;
import com.seapullkb.library.service.DashboardService;
import com.seapullkb.library.entity.TimeSlot;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class HomeController {
    private final AccountService accounts;
    private final DashboardService dashboards;
    private final Clock clock;
    public HomeController(AccountService accounts, DashboardService dashboards, Clock clock) {
        this.accounts=accounts; this.dashboards=dashboards; this.clock=clock;
    }

    @GetMapping("/")
    public String index(Authentication authentication) {
        boolean student = authentication.getAuthorities().stream()
                .anyMatch(role -> role.getAuthority().equals("ROLE_STUDENT"));
        return student ? "redirect:/student" : "redirect:/admin";
    }

    @GetMapping("/student")
    public String studentHome(Authentication authentication, Model model,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required=false) Integer slot) {
        model.addAttribute("account", accounts.findByUsername(authentication.getName()));
        var dashboard=dashboards.student(authentication.getName(),date,slot);
        model.addAttribute("dashboard",dashboard);
        model.addAttribute("catalog",dashboard.catalog());
        model.addAttribute("slots",TimeSlot.values());
        model.addAttribute("now",LocalDateTime.now(clock));
        model.addAttribute("title", "学生首页");
        return "student/home";
    }

    @GetMapping("/admin")
    public String adminHome(Authentication authentication, Model model) {
        model.addAttribute("account", accounts.findByUsername(authentication.getName()));
        model.addAttribute("superAdmin", authentication.getAuthorities().stream()
                .anyMatch(role -> role.getAuthority().equals("ROLE_SUPER_ADMIN")));
        model.addAttribute("title", "管理首页");
        return "admin/home";
    }

    @GetMapping("/forbidden")
    public String forbidden() { return "error/403"; }
}
