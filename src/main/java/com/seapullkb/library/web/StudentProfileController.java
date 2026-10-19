package com.seapullkb.library.web;

import com.seapullkb.library.service.*;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/student/profile")
public class StudentProfileController {
    private final StudentProfileService profiles;
    public StudentProfileController(StudentProfileService profiles) { this.profiles=profiles; }
    @GetMapping
    public String mine(Authentication auth,Model model) {
        var user=profiles.mine(auth.getName());
        model.addAttribute("studentProfileForm",new StudentProfileForm(user.realName(),user.className(),user.phone()));
        return page(auth,model);
    }
    @PostMapping
    public String save(Authentication auth,@Valid @ModelAttribute StudentProfileForm form,BindingResult errors,Model model,RedirectAttributes redirect) {
        if(errors.hasErrors()) return page(auth,model);
        profiles.save(auth.getName(),form);redirect.addFlashAttribute("notice","个人资料已保存。");return "redirect:/student/profile";
    }
    @PostMapping("/password")
    public String password(Authentication auth,@Valid @ModelAttribute StudentPasswordForm form,BindingResult errors,
            HttpServletRequest request,HttpServletResponse response,RedirectAttributes redirect) {
        if(errors.hasErrors()) { redirect.addFlashAttribute("error","请检查当前密码、新密码及确认：新密码8–64字符，UTF-8不超过72字节，两次输入一致。");return "redirect:/student/profile"; }
        try { profiles.password(auth.getName(),form); }
        catch(MaintenanceConflictException ex) { redirect.addFlashAttribute("error",ex.getMessage());return "redirect:/student/profile"; }
        new SecurityContextLogoutHandler().logout(request,response,auth);
        return "redirect:/login?changed";
    }
    private String page(Authentication auth,Model model) {
        model.addAttribute("profile",profiles.mine(auth.getName()));model.addAttribute("title","个人中心");return "student/profile";
    }
}
