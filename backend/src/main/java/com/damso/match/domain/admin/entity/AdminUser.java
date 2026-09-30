package com.damso.match.domain.admin.entity;

import com.damso.match.domain.member.entity.AppUser;
import com.damso.match.global.config.DataSeeder;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 관리자 계정 (주인장용).
 *  - 손님 계정({@link AppUser})과 완전히 분리된 별도 테이블이다.
 *  - 첫 실행 시 admin / admin 계정이 자동으로 만들어진다. (DataSeeder)
 */
@Entity
@Table(
        name = "admin_users",
        uniqueConstraints = @UniqueConstraint(columnNames = "username")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String username;

    /** BCrypt 해시. */
    @Column(nullable = false, length = 100)
    private String passwordHash;

    /** 기본 비밀번호(admin)를 아직 안 바꿨는지 — 화면에 변경 안내를 띄우는 데 쓴다. */
    @Column
    private Boolean initialPassword;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime lastLoginAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
