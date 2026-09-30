package com.damso.match.domain.setting.entity;

import com.damso.match.domain.setting.service.SettingsCatalog;

import jakarta.persistence.*;
import lombok.*;

/**
 * 관리자가 코드 수정 없이 조절하는 운영 설정 (key-value).
 * 기본값은 코드(SettingsCatalog)에 있고, 여기 저장된 값이 있으면 덮어쓴다.
 */
@Entity
@Table(name = "app_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppSetting {

    @Id
    @Column(name = "setting_key", length = 100)
    private String settingKey;

    @Column(name = "setting_value", columnDefinition = "TEXT")
    private String settingValue;
}
