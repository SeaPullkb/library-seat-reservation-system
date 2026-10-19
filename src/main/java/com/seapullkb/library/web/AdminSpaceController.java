package com.seapullkb.library.web;

import com.seapullkb.library.service.MaintenanceConflictException;
import com.seapullkb.library.service.SpaceService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/rooms")
public class AdminSpaceController {
    private final SpaceService spaces;
    public AdminSpaceController(SpaceService spaces) { this.spaces = spaces; }

    @GetMapping
    public String rooms(Model model) {
        model.addAttribute("roomForm", new RoomForm("", "", ""));
        return roomPage(model, null, false);
    }

    @GetMapping("/{id}/edit")
    public String editRoom(@PathVariable Long id, Model model) {
        var room = spaces.room(id, false);
        model.addAttribute("roomForm", new RoomForm(room.getRoomName(), room.getLocation(), room.getDescription()));
        return roomPage(model, id, true);
    }

    @PostMapping
    public String createRoom(@Valid @ModelAttribute RoomForm roomForm, BindingResult errors,
                             Model model, RedirectAttributes redirect) {
        if (errors.hasErrors()) return roomPage(model, null, true);
        spaces.saveRoom(null, roomForm);
        redirect.addFlashAttribute("notice", "阅览室已创建。");
        return "redirect:/admin/rooms";
    }

    @PostMapping("/{id}")
    public String updateRoom(@PathVariable Long id, @Valid @ModelAttribute RoomForm roomForm,
                             BindingResult errors, Model model, RedirectAttributes redirect) {
        if (errors.hasErrors()) return roomPage(model, id, true);
        spaces.saveRoom(id, roomForm);
        redirect.addFlashAttribute("notice", "阅览室信息已保存。");
        return "redirect:/admin/rooms";
    }

    @PostMapping("/{id}/status")
    public String roomStatus(@PathVariable Long id, @RequestParam boolean enabled, RedirectAttributes redirect) {
        try {
            spaces.setRoomEnabled(id, enabled);
            redirect.addFlashAttribute("notice", enabled ? "阅览室已启用。" : "阅览室已停用，座位原有启用状态保留。");
        } catch (MaintenanceConflictException ex) { redirect.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/admin/rooms";
    }

    @GetMapping("/{roomId}/seats")
    public String seats(@PathVariable Long roomId, Model model) {
        model.addAttribute("seatForm", new SeatForm(""));
        return seatPage(model, roomId, null, false);
    }

    @GetMapping("/{roomId}/seats/{seatId}/edit")
    public String editSeat(@PathVariable Long roomId, @PathVariable Long seatId, Model model) {
        model.addAttribute("seatForm", new SeatForm(spaces.seat(roomId, seatId).getSeatNo()));
        return seatPage(model, roomId, seatId, true);
    }

    @PostMapping("/{roomId}/seats")
    public String createSeat(@PathVariable Long roomId, @Valid @ModelAttribute SeatForm seatForm,
                             BindingResult errors, Model model, RedirectAttributes redirect) {
        return saveSeat(roomId, null, seatForm, errors, model, redirect);
    }

    @PostMapping("/{roomId}/seats/{seatId}")
    public String updateSeat(@PathVariable Long roomId, @PathVariable Long seatId,
                             @Valid @ModelAttribute SeatForm seatForm, BindingResult errors,
                             Model model, RedirectAttributes redirect) {
        return saveSeat(roomId, seatId, seatForm, errors, model, redirect);
    }

    @PostMapping("/{roomId}/seats/{seatId}/status")
    public String seatStatus(@PathVariable Long roomId, @PathVariable Long seatId,
                             @RequestParam boolean enabled, RedirectAttributes redirect) {
        try {
            spaces.setSeatEnabled(roomId, seatId, enabled);
            redirect.addFlashAttribute("notice", enabled ? "座位已启用。" : "座位已停用。");
        } catch (MaintenanceConflictException ex) { redirect.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/admin/rooms/" + roomId + "/seats";
    }

    @PostMapping("/{roomId}/seats/{seatId}/delete")
    public String deleteSeat(@PathVariable Long roomId, @PathVariable Long seatId, RedirectAttributes redirect) {
        try {
            spaces.deleteSeat(roomId, seatId);
            redirect.addFlashAttribute("notice", "座位已删除。");
        } catch (MaintenanceConflictException ex) { redirect.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/admin/rooms/" + roomId + "/seats";
    }

    private String saveSeat(Long roomId, Long seatId, SeatForm form, BindingResult errors,
                             Model model, RedirectAttributes redirect) {
        if (!errors.hasErrors()) {
            try {
                spaces.saveSeat(roomId, seatId, form);
                redirect.addFlashAttribute("notice", seatId == null ? "座位已添加。" : "座位编号已保存。");
                return "redirect:/admin/rooms/" + roomId + "/seats";
            } catch (MaintenanceConflictException ex) { errors.rejectValue("seatNo", "duplicate", ex.getMessage()); }
        }
        return seatPage(model, roomId, seatId, true);
    }

    private String roomPage(Model model, Long editingId, boolean showForm) {
        model.addAttribute("title", "阅览室管理"); model.addAttribute("rooms", spaces.listRooms(false));
        model.addAttribute("editingId", editingId); model.addAttribute("showForm", showForm);
        return "admin/rooms";
    }
    private String seatPage(Model model, Long roomId, Long editingId, boolean showForm) {
        model.addAttribute("title", "座位管理"); model.addAttribute("room", spaces.room(roomId, false));
        model.addAttribute("seats", spaces.listSeats(roomId));
        model.addAttribute("editingId", editingId); model.addAttribute("showForm", showForm);
        return "admin/seats";
    }
}
