package com.seapullkb.library.web;

import com.seapullkb.library.service.MaintenanceConflictException;
import com.seapullkb.library.service.SpaceService;
import com.seapullkb.library.service.AdminAccess;
import java.util.Locale;
import java.util.Map;
import org.springframework.security.core.Authentication;
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
    private final AdminAccess access;
    public AdminSpaceController(SpaceService spaces,AdminAccess access) { this.spaces = spaces; this.access=access; }

    @GetMapping
    public String rooms(Model model, @RequestParam(defaultValue="") String q, @RequestParam(required=false) Integer state) {
        filters(model,q,state);
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
    public String createRoom(Authentication auth, @Valid @ModelAttribute RoomForm roomForm, BindingResult errors,
                             Model model, RedirectAttributes redirect) {
        if (errors.hasErrors()) return roomPage(model, null, true);
        access.write(auth.getName(),() -> spaces.saveRoom(null, roomForm));
        redirect.addFlashAttribute("notice", "阅览室已创建。");
        return "redirect:/admin/rooms";
    }

    @PostMapping("/{id}")
    public String updateRoom(Authentication auth, @PathVariable Long id, @Valid @ModelAttribute RoomForm roomForm,
                             BindingResult errors, Model model, RedirectAttributes redirect) {
        if (errors.hasErrors()) return roomPage(model, id, true);
        access.write(auth.getName(),() -> spaces.saveRoom(id, roomForm));
        redirect.addFlashAttribute("notice", "阅览室信息已保存。");
        return "redirect:/admin/rooms";
    }

    @PostMapping("/{id}/status")
    public String roomStatus(Authentication auth, @PathVariable Long id, @RequestParam boolean enabled, RedirectAttributes redirect) {
        try {
            access.write(auth.getName(),() -> spaces.setRoomEnabled(id, enabled));
            redirect.addFlashAttribute("notice", enabled ? "阅览室已启用。" : "阅览室已停用，座位原有启用状态保留。");
        } catch (MaintenanceConflictException ex) { redirect.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/admin/rooms";
    }

    @GetMapping("/{roomId}/seats")
    public String seats(@PathVariable Long roomId, Model model, @RequestParam(defaultValue="") String q,
                        @RequestParam(required=false) Integer state) {
        filters(model,q,state);
        model.addAttribute("seatForm", new SeatForm(""));
        return seatPage(model, roomId, null, false);
    }

    @GetMapping("/{roomId}/seats/{seatId}/edit")
    public String editSeat(@PathVariable Long roomId, @PathVariable Long seatId, Model model) {
        model.addAttribute("seatForm", new SeatForm(spaces.seat(roomId, seatId).getSeatNo()));
        return seatPage(model, roomId, seatId, true);
    }

    @PostMapping("/{roomId}/seats")
    public String createSeat(Authentication auth, @PathVariable Long roomId, @Valid @ModelAttribute SeatForm seatForm,
                             BindingResult errors, Model model, RedirectAttributes redirect) {
        return saveSeat(auth.getName(),roomId, null, seatForm, errors, model, redirect);
    }

    @PostMapping("/{roomId}/seats/{seatId}")
    public String updateSeat(Authentication auth, @PathVariable Long roomId, @PathVariable Long seatId,
                             @Valid @ModelAttribute SeatForm seatForm, BindingResult errors,
                             Model model, RedirectAttributes redirect) {
        return saveSeat(auth.getName(),roomId, seatId, seatForm, errors, model, redirect);
    }

    @PostMapping("/{roomId}/seats/{seatId}/status")
    public String seatStatus(Authentication auth, @PathVariable Long roomId, @PathVariable Long seatId,
                             @RequestParam boolean enabled, RedirectAttributes redirect) {
        try {
            access.write(auth.getName(),() -> spaces.setSeatEnabled(roomId, seatId, enabled));
            redirect.addFlashAttribute("notice", enabled ? "座位已启用。" : "座位已停用。");
        } catch (MaintenanceConflictException ex) { redirect.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/admin/rooms/" + roomId + "/seats";
    }

    @PostMapping("/{roomId}/seats/{seatId}/delete")
    public String deleteSeat(Authentication auth, @PathVariable Long roomId, @PathVariable Long seatId, RedirectAttributes redirect) {
        try {
            access.write(auth.getName(),() -> spaces.deleteSeat(roomId, seatId));
            redirect.addFlashAttribute("notice", "座位已删除。");
        } catch (MaintenanceConflictException ex) { redirect.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/admin/rooms/" + roomId + "/seats";
    }

    private String saveSeat(String actor,Long roomId, Long seatId, SeatForm form, BindingResult errors,
                             Model model, RedirectAttributes redirect) {
        if (!errors.hasErrors()) {
            try {
                access.write(actor,() -> spaces.saveSeat(roomId, seatId, form));
                redirect.addFlashAttribute("notice", seatId == null ? "座位已添加。" : "座位编号已保存。");
                return "redirect:/admin/rooms/" + roomId + "/seats";
            } catch (MaintenanceConflictException ex) { errors.rejectValue("seatNo", "duplicate", ex.getMessage()); }
        }
        return seatPage(model, roomId, seatId, true);
    }

    private String roomPage(Model model, Long editingId, boolean showForm) {
        var all=spaces.listRooms(false);
        String q=query(model); Integer state=(Integer)model.getAttribute("state");
        model.addAttribute("title", "阅览室管理");
        model.addAttribute("rooms",all.stream().filter(r -> state==null||r.status().equals(state))
                .filter(r -> (r.name()+" "+r.location()).toLowerCase(Locale.ROOT).contains(q.toLowerCase(Locale.ROOT))).toList());
        long enabled=all.stream().filter(r -> r.status()==1).count();
        model.addAttribute("spaceSummary",Map.of("total",(long)all.size(),"enabled",enabled,"disabled",all.size()-enabled,
                "capacity",all.stream().mapToLong(r -> r.enabledSeats()).sum()));
        model.addAttribute("editingId", editingId); model.addAttribute("showForm", showForm);
        return "admin/rooms";
    }
    private String seatPage(Model model, Long roomId, Long editingId, boolean showForm) {
        model.addAttribute("title", "座位管理"); model.addAttribute("room", spaces.room(roomId, false));
        var all=spaces.listSeats(roomId);
        String q=query(model); Integer state=(Integer)model.getAttribute("state");
        var filtered=all.stream().filter(s -> state==null||s.getStatus().equals(state))
                .filter(s -> s.getSeatNo().toLowerCase(Locale.ROOT).contains(q.toLowerCase(Locale.ROOT))).toList();
        model.addAttribute("seats",filtered);
        long enabled=all.stream().filter(s -> s.getStatus()==1).count();
        model.addAttribute("spaceSummary",Map.of("total",(long)all.size(),"enabled",enabled,"disabled",all.size()-enabled,
                "matched",(long)filtered.size()));
        model.addAttribute("editingId", editingId); model.addAttribute("showForm", showForm);
        return "admin/seats";
    }

    private void filters(Model model,String q,Integer state) {
        if(q.length()>100 || (state!=null && state!=0 && state!=1)) throw new IllegalArgumentException("搜索条件无效。");
        model.addAttribute("q",q.strip()); model.addAttribute("state",state);
    }
    private String query(Model model) {
        String q=(String)model.getAttribute("q");
        if(q==null) { q="";model.addAttribute("q",q); }
        return q;
    }
}
