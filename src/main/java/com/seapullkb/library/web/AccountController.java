package com.seapullkb.library.web;

import com.seapullkb.library.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AccountController {
    private final AccountService accounts;
    public AccountController(AccountService accounts) { this.accounts = accounts; }

    @GetMapping("/login")
    public String login() { return "auth/login"; }

    @GetMapping("/register")
    public String register(Model model) {
        model.addAttribute("registrationForm", new RegistrationForm("", "", "", "", "", ""));
        return "auth/register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute RegistrationForm registrationForm,
                           BindingResult result, RedirectAttributes redirect) {
        if (result.hasErrors()) return "auth/register";
        try {
            accounts.registerStudent(registrationForm);
        } catch (AccountService.UsernameTakenException ex) {
            result.rejectValue("username", "duplicate", ex.getMessage());
            return "auth/register";
        }
        redirect.addFlashAttribute("notice", "注册成功，请使用新账号登录。");
        return "redirect:/login";
    }
}
