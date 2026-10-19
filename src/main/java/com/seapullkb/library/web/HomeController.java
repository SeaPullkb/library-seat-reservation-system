package com.seapullkb.library.web;

import com.seapullkb.library.service.AccountService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {
    private final AccountService accounts;
    public HomeController(AccountService accounts) { this.accounts = accounts; }

    @GetMapping("/")
    public String index(Authentication authentication) {
        boolean student = authentication.getAuthorities().stream()
                .anyMatch(role -> role.getAuthority().equals("ROLE_STUDENT"));
        return student ? "redirect:/student" : "redirect:/admin";
    }

    @GetMapping("/student")
    public String studentHome(Authentication authentication, Model model) {
        model.addAttribute("account", accounts.findByUsername(authentication.getName()));
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
