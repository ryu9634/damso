package com.damso.match.domain.notification.entity;

import com.damso.match.domain.notification.service.NotificationService;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 앱 회원에게 보낸 알림 한 건 (알림함).
 *  - 좋아요를 받았을 때, 상대가 답했을 때, 만남 약속이 오갈 때 쌓인다.
 *  - 휴대폰 푸시(FCM 등)는 아직 붙이지 않았고, {@code NotificationService} 한 곳에서
 *    실제 발송을 시도하도록 자리를 만들어 뒀다. 지금은 알림함 + 브라우저 알림으로 전달한다.
 */
@Entity
@Table(name = "app_notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 받는 회원 id. */
    @Column(nullable = false)
    private Long userId;

    /** 알림 종류 (앱에서 아이콘 고를 때 쓴다). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationType type;

    @Column(nullable = false, length = 60)
    private String title;

    @Column(length = 300)
    private String body;

    /** 눌렀을 때 이동할 앱 경로 (예: /likes). */
    @Column(length = 100)
    private String linkPath;

    /** 읽은 시각. NULL 이면 안 읽음. */
    @Column
    private LocalDateTime readAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
