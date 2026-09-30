package com.damso.match.domain.setting.repository;

import com.damso.match.domain.setting.entity.AppSetting;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AppSettingRepository extends JpaRepository<AppSetting, String> {
}
