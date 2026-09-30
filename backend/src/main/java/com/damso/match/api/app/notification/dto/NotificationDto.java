package com.damso.match.api.app.notification.dto;

import com.damso.match.domain.notification.entity.AppNotification;
import com.damso.match.domain.notification.entity.NotificationType;

import java.time.LocalDateTime;

/** 알림함 한 줄. */
public record NotificationDto(
        Long id,
        NotificationType type,
        String title,
        String body,
        String linkPath,
        boolean read,
        LocalDateTime createdAt
) {
    public static NotificationDto from(AppNotification n) {
        return new NotificationDto(
                n.getId(), n.getType(), n.getTitle(), n.getBody(), n.getLinkPath(),
                n.getReadAt() != null, n.getCreatedAt());
    }
}
