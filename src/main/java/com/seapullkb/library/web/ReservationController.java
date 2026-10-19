package com.seapullkb.library.web;

import com.seapullkb.library.entity.ReservationStatus;
import com.seapullkb.library.service.ReservationConflictException;
import com.seapullkb.library.service.ReservationService;
import com.seapullkb.library.service.ReservationLifecycleService;
import com.seapullkb.library.service.LifecycleResult;
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
    private final ReservationLifecycleService lifecycle;
    public ReservationController(ReservationService reservations, Clock clock, ReservationLifecycleService lifecycle) {
        this.reservations = reservations; this.clock = clock; this.lifecycle = lifecycle;
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

    @PostMapping("/reservations/{id}/checkin")
    public String checkIn(@PathVariable Long id, Authentication authentication, RedirectAttributes redirect) {
        try {
            LifecycleResult result = lifecycle.checkIn(authentication.getName(), id);
            redirect.addFlashAttribute("notice", result == LifecycleResult.CHECKED_IN ? "签到成功，祝你学习顺利。" : "你已签到，无需重复操作。");
        } catch (ReservationConflictException ex) { redirect.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/student/reservations";
    }

    @PostMapping("/reservations/{id}/checkout")
    public String checkOut(@PathVariable Long id, Authentication authentication, RedirectAttributes redirect) {
        try {
            LifecycleResult result = lifecycle.checkOut(authentication.getName(), id);
            String message = switch (result) {
                case CHECKED_OUT -> "签退成功。本座位及你的当前日期时段仍保留使用记录，不再开放重复预约。";
                case AUTO_COMPLETED -> "时段已结束，预约已自动完成。";
                default -> "预约已完成，无需重复签退。";
            };
            redirect.addFlashAttribute("notice", message);
        } catch (ReservationConflictException ex) { redirect.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/student/reservations";
    }
}
