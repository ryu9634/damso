package com.damso.match.api.admin.event;

import com.damso.match.api.admin.event.dto.StatsDto;
import com.damso.match.domain.event.service.StatsService;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/admin/stats")
@RequiredArgsConstructor
public class AdminStatsController {

    private final StatsService statsService;

    /** from/to 는 yyyy-MM-dd (미지정 시 전체 기간). to 는 해당 날짜 포함. */
    @GetMapping
    public StatsDto get(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return statsService.summarize(from, to);
    }
}
