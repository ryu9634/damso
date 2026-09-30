package com.damso.match.domain.setting.service;

import com.damso.match.api.admin.setting.dto.SettingItemDto;
import com.damso.match.api.guest.dto.GuestSettingsDto;
import com.damso.match.domain.setting.entity.AppSetting;
import com.damso.match.domain.setting.repository.AppSettingRepository;
import static com.damso.match.domain.setting.service.SettingsCatalog.*;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SettingService {

    private final AppSettingRepository repo;

    /** 저장된 값(override) 맵. */
    private Map<String, String> stored() {
        Map<String, String> m = new HashMap<>();
        repo.findAll().forEach(s -> m.put(s.getSettingKey(), s.getSettingValue()));
        return m;
    }

    /** 카탈로그 기본값 + 저장값을 합쳐 최종값을 돌려준다. */
    public String get(String key) {
        String v = repo.findById(key).map(AppSetting::getSettingValue).orElse(null);
        if (v != null && !v.isBlank()) return v;
        Field f = byKey(key);
        return f == null ? null : f.defaultValue();
    }

    public int getInt(String key) {
        try {
            return Integer.parseInt(get(key).trim());
        } catch (Exception e) {
            Field f = byKey(key);
            return f == null ? 0 : Integer.parseInt(f.defaultValue());
        }
    }

    /** 관리자 화면용: 정의 + 현재값 목록. */
    public List<SettingItemDto> listForAdmin() {
        Map<String, String> saved = stored();
        List<SettingItemDto> out = new ArrayList<>();
        for (Field f : ADMIN_FIELDS) {
            String v = saved.getOrDefault(f.key(), null);
            if (v == null || v.isBlank()) v = f.defaultValue();
            out.add(new SettingItemDto(f.key(), f.label(), f.group(), f.type().name(), v, f.options()));
        }
        return out;
    }

    /** 관리자 저장: 카탈로그에 있는 키만 반영. NUMBER 는 최소 1 로 보정. */
    @Transactional
    public void update(Map<String, String> values) {
        if (values == null) return;
        for (Map.Entry<String, String> e : values.entrySet()) {
            Field f = byKey(e.getKey());
            if (f == null) continue;
            String val = e.getValue() == null ? "" : e.getValue();
            if (f.type() == Type.NUMBER) {
                int n;
                try { n = Integer.parseInt(val.trim()); } catch (Exception ex) { n = Integer.parseInt(f.defaultValue()); }
                if (n < 1) n = 1;
                val = String.valueOf(n);
            }
            // SELECT 은 정해진 선택지만 저장한다. (모르는 값이면 기본값으로 되돌림)
            if (f.type() == Type.SELECT && !f.options().contains(val)) {
                val = f.defaultValue();
            }
            repo.save(AppSetting.builder().settingKey(f.key()).settingValue(val).build());
        }
    }

    /** 기본값으로 초기화 (저장값 전체 삭제). */
    @Transactional
    public void resetDefaults() {
        repo.deleteAll();
    }

    /** 손님 화면용 값 묶음. */
    public GuestSettingsDto guestSettings() {
        return new GuestSettingsDto(
                getInt(MAX_MESSAGE_RECIPIENTS),
                get(GUEST_PREFERENCE_TITLE),
                get(GUEST_PREFERENCE_SUBTITLE),
                get(GUEST_MESSAGE_TITLE),
                get(GUEST_MESSAGE_SUBTITLE),
                get(GUEST_WAITING_TITLE),
                get(GUEST_WAITING_SUBTITLE),
                get(GUEST_END_TITLE),
                get(GUEST_END_SUBTITLE)
        );
    }
}
