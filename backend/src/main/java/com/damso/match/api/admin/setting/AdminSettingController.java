package com.damso.match.api.admin.setting;

import com.damso.match.api.admin.setting.dto.SettingItemDto;
import com.damso.match.domain.setting.service.SettingService;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/settings")
@RequiredArgsConstructor
public class AdminSettingController {

    private final SettingService settingService;

    @GetMapping
    public List<SettingItemDto> list() {
        return settingService.listForAdmin();
    }

    /** { "key": "value", ... } 형태로 저장 (카탈로그에 있는 키만 반영). */
    @PutMapping
    public List<SettingItemDto> update(@RequestBody Map<String, String> values) {
        settingService.update(values);
        return settingService.listForAdmin();
    }

    @PostMapping("/reset-default")
    public List<SettingItemDto> resetDefault() {
        settingService.resetDefaults();
        return settingService.listForAdmin();
    }
}
