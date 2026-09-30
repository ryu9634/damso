package com.damso.match.api.admin.setting.dto;

import java.util.List;

/** 관리자 환경설정 화면에 표시할 항목 (정의 + 현재값). */
public record SettingItemDto(
        String key,
        String label,
        String group,
        String type,   // NUMBER | TEXT | TEXTAREA | SELECT
        String value,
        List<String> options   // SELECT 일 때만 채워진다
) {}
