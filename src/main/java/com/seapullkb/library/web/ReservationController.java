package com.seapullkb.library.web;

import com.seapullkb.library.entity.ReservationStatus;
import com.seapullkb.library.service.ReservationConflictException;
import com.seapullkb.library.service.ReservationService;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/student")
public class ReservationController {
    private final ReservationService reservations;
    private final Clock clock;
    public ReservationController(ReservationService reservations, Clock clock) {
        this.reservations = reservations; this.clock = clock;
    }

    @PostMapping("/rooms/{roomId}/seats/{seatId}/reservations")
    public String create(@PathVariable Long roomId, @PathVariable Long seatId,
                          @Valid @ModelAttribute BookingForm bookingForm, BindingResult errors,
                          Authentication authentication, RedirectAttributes redirect) {
        if (errors.hasErrors()) throw new IllegalArgumentException("预约日期或时段无效。");
        try {
            reservations.create(authentication.getName(), roomId, seatId, bookingForm.date(), bookingForm.slot());
            redirect.addFlashAttribute("notice", "预约成功，请在“我的预约”中查看签到截止时间。");
            return "redirect:/student/reservations";
        } catch (ReservationConflictException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
            redirect.addAttribute("date", bookingForm.date().toString()); redirect.addAttribute("slot", bookingForm.slot());
            return "redirect:/student/rooms/" + roomId + "/seats";
        }
    }

    @GetMapping("/reservations")
    public String mine(@RequestParam(required = false) ReservationStatus status,
                        @RequestParam(defaultValue = "0") int page, Authentication authentication, Model model) {
        model.addAttribute("title", "我的预约");
        model.addAttribute("reservations", reservations.mine(authentication.getName(), status, page));
        model.addAttribute("statuses", ReservationStatus.values()); model.addAttribute("selectedStatus", status);
        model.addAttribute("now", LocalDateTime.now(clock));
        return "student/reservations";
    }

    @PostMapping("/reservations/{id}/cancel")
    public String cancel(@PathVariable Long id, Authentication authentication, RedirectAttributes redirect) {
        try {
            boolean changed = reservations.cancel(authentication.getName(), id);
            redirect.addFlashAttribute("notice", changed ? "预约已取消，座位已重新开放。" : "该预约已取消，无需重复操作。");
        } catch (ReservationConflictException ex) { redirect.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/student/reservations";
    }
}
