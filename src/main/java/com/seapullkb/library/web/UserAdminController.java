package com.seapullkb.library.web;

import com.seapullkb.library.entity.Role;
import com.seapullkb.library.service.*;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/users")
public class UserAdminController {
    private final UserAdminService users;
    public UserAdminController(UserAdminService users) { this.users=users; }
    @GetMapping
    public String list(Authentication auth,@RequestParam(defaultValue="") String keyword,@RequestParam(required=false) Role role,
            @RequestParam(required=false) Integer status,@RequestParam(defaultValue="0") int page,Model model) {
        model.addAttribute("users",users.search(auth.getName(),keyword,role,status,page));
        model.addAttribute("keyword",keyword); model.addAttribute("role",role); model.addAttribute("status",status);
        model.addAttribute("roles",Role.values()); model.addAttribute("title","用户管理"); return "admin/users";
    }
    @GetMapping("/new")
    public String newAdmin(Authentication auth,Model model) {
        users.search(auth.getName(),"",null,null,0);
        model.addAttribute("adminCreateForm",new AdminCreateForm("","","",""));
        model.addAttribute("title","新增管理员"); return "admin/user-new";
    }
    @PostMapping
    public String create(Authentication auth,@Valid @ModelAttribute AdminCreateForm form,BindingResult errors,Model model,RedirectAttributes redirect) {
        if(!errors.hasErrors()) {
            try { Long id=users.createAdministrator(auth.getName(),form); redirect.addFlashAttribute("notice","图书馆管理员已创建。"); return "redirect:/admin/users/"+id; }
            catch(AccountService.UsernameTakenException ex) { errors.rejectValue("username","duplicate",ex.getMessage()); }
        }
        model.addAttribute("title","新增管理员"); return "admin/user-new";
    }
    @GetMapping("/{id}")
    public String detail(Authentication auth,@PathVariable Long id,Model model) {
        var account=users.detail(auth.getName(),id); model.addAttribute("userEditForm",new UserEditForm(account.realName(),account.className(),account.phone()));
        return detailPage(auth,id,model);
    }
    private String detailPage(Authentication auth,Long id,Model model) {
        var account=users.detail(auth.getName(),id);
        model.addAttribute("managedUser",account); model.addAttribute("self",account.username().equalsIgnoreCase(auth.getName()));
        model.addAttribute("roles",Role.values()); model.addAttribute("title","维护用户"); return "admin/user-edit";
    }
    @PostMapping("/{id}")
    public String edit(Authentication auth,@PathVariable Long id,@Valid @ModelAttribute UserEditForm form,BindingResult errors,Model model,RedirectAttributes redirect) {
        if(errors.hasErrors()) return detailPage(auth,id,model);
        users.edit(auth.getName(),id,form); redirect.addFlashAttribute("notice","用户资料已保存。"); return "redirect:/admin/users/"+id;
    }
    @PostMapping("/{id}/role")
    public String role(Authentication auth,@PathVariable Long id,@RequestParam Role role,RedirectAttributes redirect) {
        try { users.role(auth.getName(),id,role); redirect.addFlashAttribute("notice","角色已更新，原登录会话已失效。"); }
        catch(MaintenanceConflictException ex) { redirect.addFlashAttribute("error",ex.getMessage()); }
        return "redirect:/admin/users/"+id;
    }
    @PostMapping("/{id}/status")
    public String status(Authentication auth,@PathVariable Long id,@RequestParam boolean enabled,RedirectAttributes redirect) {
        try { users.enabled(auth.getName(),id,enabled); redirect.addFlashAttribute("notice",enabled?"账号已启用，请重新登录。":"账号已禁用，原登录会话已失效。"); }
        catch(MaintenanceConflictException ex) { redirect.addFlashAttribute("error",ex.getMessage()); }
        return "redirect:/admin/users/"+id;
    }
    @PostMapping("/{id}/password")
    public String password(Authentication auth,@PathVariable Long id,@Valid @ModelAttribute PasswordResetForm form,BindingResult errors,RedirectAttributes redirect) {
        if(errors.hasErrors()) redirect.addFlashAttribute("error","新密码须为12–64个字符、UTF-8不超过72字节，且两次输入一致。");
        else { users.resetPassword(auth.getName(),id,form); redirect.addFlashAttribute("notice","密码已重置，原登录会话已失效。请通过安全方式告知用户新密码。"); }
        return "redirect:/admin/users/"+id;
    }
}
