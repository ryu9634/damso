package com.damso.match.domain.member.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 앱 회원 로그인 세션 (불투명 토큰).
 *  - 매장 내부용 서비스라 별도 인증 프레임워크 없이 랜덤 토큰만 발급해 둔다.
 *  - 클라이언트는 Authorization: Bearer &lt;token&gt; 으로 보낸다.
 */
@Entity
@Table(name = "user_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSession {

    /** 세션 토큰 자체가 PK. */
    @Id
    @Column(length = 64)
    private String token;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    @Transient
    public boolean isExpired() {
        return expiresAt == null || expiresAt.isBefore(LocalDateTime.now());
    }
}