package com.damso.match.api.admin.event;

import com.damso.match.api.admin.event.dto.PreferenceDto;
import com.damso.match.domain.event.service.PreferenceService;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/preferences")
@RequiredArgsConstructor
public class AdminPreferenceController {

    private final PreferenceService preferenceService;

    @GetMapping
    public List<PreferenceDto> list(@RequestParam Long eventId) {
        return preferenceService.listWithNicknames(eventId);
    }
}
