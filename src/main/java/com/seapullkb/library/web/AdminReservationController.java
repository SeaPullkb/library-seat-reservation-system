package com.seapullkb.library.web;

import com.seapullkb.library.entity.*;
import com.seapullkb.library.service.*;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/reservations")
public class AdminReservationController {
    private final AdminReservationService reservations;
    private final SpaceService spaces;
    public AdminReservationController(AdminReservationService reservations,SpaceService spaces) { this.reservations=reservations; this.spaces=spaces; }
    @GetMapping
    public String list(Authentication auth, @RequestParam(defaultValue="") String keyword, @RequestParam(required=false) Long roomId,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required=false) Integer slot,@RequestParam(required=false) ReservationStatus status,
            @RequestParam(defaultValue="0") int page, Model model) {
        var filter=new AdminReservationFilter(keyword,roomId,date,slot,status);
        model.addAttribute("reservations",reservations.search(auth.getName(),filter,page));
        model.addAttribute("filter",filter); model.addAttribute("rooms",spaces.listRooms(false));
        model.addAttribute("slots",TimeSlot.values()); model.addAttribute("statuses",ReservationStatus.values());
        model.addAttribute("title","预约管理"); return "admin/reservations";
    }
    @PostMapping("/{id}/cancel")
    public String cancel(Authentication auth,@PathVariable Long id,@RequestParam String reason,RedirectAttributes redirect) {
        try {
            var result=reservations.cancel(auth.getName(),id,reason);
            redirect.addFlashAttribute("notice",result==LifecycleResult.CANCELLED?"预约已取消，操作人、原因及时间已保存。":"此预约已取消，原操作记录保持不变。");
        } catch(ReservationConflictException|IllegalArgumentException ex) { redirect.addFlashAttribute("error",ex.getMessage()); }
        return "redirect:/admin/reservations";
    }
}
