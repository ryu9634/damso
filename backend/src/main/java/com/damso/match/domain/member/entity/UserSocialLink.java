package com.damso.match.domain.member.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 회원 계정에 연결된 간편로그인 수단.
 *  - (provider, providerId) 가 유일하므로 같은 카카오 계정이 두 회원에게 붙는 일이 없다.
 *  - 한 회원이 카카오·네이버를 모두 연결해도 계정은 하나로 유지된다.
 */
@Entity
@Table(
        name = "user_social_links",
        uniqueConstraints = @UniqueConstraint(columnNames = {"provider", "providerId"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSocialLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuthProvider provider;

    /** 제공자가 준 회원 식별자. */
    @Column(nullable = false, length = 100)
    private String providerId;

    @Column(nullable = false)
    private LocalDateTime linkedAt;

    @PrePersist
    public void prePersist() {
        if (linkedAt == null) linkedAt = LocalDateTime.now();
    }
}
