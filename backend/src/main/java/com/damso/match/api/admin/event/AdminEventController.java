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

    @GetMapping("/{id}")
    public Event get(@PathVariable Long id) {
        return eventService.get(id);
    }

    @PostMapping
    public Event create(@RequestBody Map<String, String> body) {
        return eventService.create(body.getOrDefault("name", "담소혼술바 매칭 이벤트"));
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
