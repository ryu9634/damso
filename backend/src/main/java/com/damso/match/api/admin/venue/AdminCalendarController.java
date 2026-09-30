package com.damso.match.api.admin.venue;

import com.damso.match.api.admin.venue.dto.CalendarEntryDto;
import com.damso.match.domain.venue.service.CalendarService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 관리자 — 가게 캘린더(행사 · 휴일) 관리. */
@RestController
@RequestMapping("/api/admin/calendar")
@RequiredArgsConstructor
public class AdminCalendarController {

    private final CalendarService calendarService;

    @GetMapping
    public List<CalendarEntryDto> list() {
        return calendarService.listAll();
    }

    @PostMapping
    public CalendarEntryDto create(@Valid @RequestBody CalendarEntryDto.SaveRequest req) {
        return calendarService.create(req);
    }

    @PutMapping("/{id}")
    public CalendarEntryDto update(@PathVariable Long id,
                                   @Valid @RequestBody CalendarEntryDto.SaveRequest req) {
        return calendarService.update(id, req);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        calendarService.delete(id);
    }
}
