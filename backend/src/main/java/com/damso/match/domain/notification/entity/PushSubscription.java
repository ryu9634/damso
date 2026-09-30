package com.damso.match.domain.notification.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 브라우저(휴대폰 포함)가 발급한 웹 푸시 구독 정보.
 *  - 한 회원이 기기마다 하나씩 가진다. (폰 + 노트북이면 두 줄)
 *  - endpoint 는 발송 주소이자 고유 키다. 브라우저를 지우거나 알림을 끄면 죽고,
 *    발송 시 404/410 이 오면 그 자리에서 지운다.
 */
@Entity
@Table(
        name = "push_subscriptions",
        uniqueConstraints = @UniqueConstraint(columnNames = "endpoint")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PushSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long appUserId;

    /** 푸시 서비스(구글·애플 등)가 준 발송 주소. */
    @Column(nullable = false, length = 500)
    private String endpoint;

    /** 이 기기의 공개키 (payload 암호화용). */
    @Column(nullable = false, length = 255)
    private String p256dh;

    /** 이 기기의 인증 비밀값. */
    @Column(nullable = false, length = 100)
    private String auth;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    /** 마지막으로 발송에 성공한 시각. */
    @Column
    private LocalDateTime lastSentAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
