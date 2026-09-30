package com.damso.match.domain.notification.repository;

import com.damso.match.domain.notification.entity.AppNotification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AppNotificationRepository extends JpaRepository<AppNotification, Long> {

    List<AppNotification> findByUserIdOrderByCreatedAtDesc(Long userId);

    int countByUserIdAndReadAtIsNull(Long userId);

    void deleteByUserId(Long userId);
}
