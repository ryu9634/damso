package com.damso.match.api.admin.event;

import com.damso.match.domain.event.entity.Event;
import com.damso.match.domain.event.entity.EventStatus;
import com.damso.match.domain.event.service.EventService;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/events")
@RequiredArgsConstructor
public class AdminEventController {

    private final EventService eventService;

    @GetMapping
    public List<Event> list() {
        return eventService.listAll();
    }

    @GetMapping("/current")
    public Event current() {
        return eventService.getOrCreateCurrent();
    }

    @PostMapping("/start-new")
    public Event startNew(@RequestBody Map<String, String> body) {
        return eventService.startNew(body == null ? null : body.get("name"));
    }

    @PostMapping("/{id}/clear")
    public void clear(@PathVariable Long id) {
        eventService.clearEventData(id);
    }

    @PatchMapping("/{id}/status")
    public Event updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return eventService.updateStatus(id, EventStatus.valueOf(body.get("status")));
    }
}
