package com.damso.match.api.admin.event;

import com.damso.match.api.admin.event.dto.DashboardDto;
import com.damso.match.domain.event.service.DashboardService;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    public DashboardDto get(@RequestParam Long eventId) {
        return dashboardService.summarize(eventId);
    }
}
