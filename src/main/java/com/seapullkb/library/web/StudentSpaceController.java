package com.seapullkb.library.web;

import com.seapullkb.library.entity.TimeSlot;
import com.seapullkb.library.service.AccountService;
import com.seapullkb.library.service.SpaceService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.stream.IntStream;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/student/rooms")
public class StudentSpaceController {
    private final SpaceService spaces;
    private final AccountService accounts;
    private final Clock clock;
    public StudentSpaceController(SpaceService spaces, AccountService accounts, Clock clock) {
        this.spaces = spaces; this.accounts = accounts; this.clock = clock;
    }
    @GetMapping
    public String rooms(Model model) {
        model.addAttribute("title", "阅览室"); model.addAttribute("rooms", spaces.listRooms(true));
        return "student/rooms";
    }
    @GetMapping("/{id}/seats")
    public String seats(@PathVariable Long id,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                         @RequestParam(defaultValue = "1") int slot,
                         Authentication authentication, Model model) {
        LocalDate today = LocalDate.now(clock);
        LocalDate selected = date == null ? today : date;
        model.addAttribute("title", "座位浏览"); model.addAttribute("room", spaces.room(id, true));
        model.addAttribute("seats", spaces.studentGrid(id, selected, slot, accounts.findByUsername(authentication.getName()).getId()));
        model.addAttribute("dates", IntStream.range(0, 7).mapToObj(today::plusDays).toList());
        model.addAttribute("date", selected); model.addAttribute("slot", slot); model.addAttribute("slots", TimeSlot.values());
        return "student/seats";
    }
}
