package com.damso.match.domain.member.entity;

import com.damso.match.domain.dating.entity.DatingProfile;
import com.damso.match.global.security.EncryptedStringConverter;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;

/**
 * 앱(모바일) 회원 계정.
 *  - 홈/방명록/마이페이지에서 쓰는 통합 계정. 소개팅 프로필(DatingProfile)과는 별개다.
 *  - 로그인 수단은 계정 자체와 분리한다.
 *      · 아이디/비밀번호 → loginId + passwordHash
 *      · 네이버·카카오 간편로그인 → {@link UserSocialLink} (계정 하나에 여러 개 연결 가능)
 *    이렇게 두면 나중에 같은 사람이 아이디로도, 카카오로도 들어와도 한 계정에 붙일 수 있다.
 *  - 성명·생년월일은 회원 확인용으로만 저장하고 다른 손님에게는 노출하지 않는다.
 */
@Entity
@Table(
        name = "app_users",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = "loginId"),
                @UniqueConstraint(columnNames = "nickname")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppUser {

    /** 만 나이 하한 — 이 나이 미만은 가입할 수 없다. */
    public static final int MIN_AGE = 18;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ─── 로그인 수단 ────────────────────────────────────
    /** 로그인 아이디(소문자 저장). 간편로그인만 쓰는 계정은 비어 있을 수 있다. */
    @Column(length = 30)
    private String loginId;

    /** BCrypt 해시. 평문 비밀번호는 어디에도 저장하지 않는다. */
    @Column(length = 100)
    private String passwordHash;

    // ─── 가입 필수 정보 ─────────────────────────────────
    /** 다른 손님에게 보이는 이름. 방명록 등 공개 화면에는 성명 대신 이 값만 쓴다. */
    @Column(length = 20)
    private String nickname;

    /** 실명. 회원 확인용으로만 쓰고 공개 화면에는 노출하지 않는다. DB 에는 암호화해서 둔다. */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(nullable = false, length = 255)
    private String name;

    /**
     * 생년월일. 아이디 가입은 필수지만, 소셜 가입은 제공자가 안 줄 수 있어 nullable 이다.
     * 비어 있으면 마이페이지에서 보완 입력을 받는다. ({@link #isProfileComplete()})
     */
    @Column
    private LocalDate birthDate;

    /**
     * 휴대폰번호 (숫자만 저장). 가입 필수 항목이다.
     * 간편로그인으로 만든 계정은 제공자가 안 줄 수 있어 nullable 이며,
     * 이 경우 마이페이지에서 보완 입력을 받는다. ({@link #isProfileComplete()})
     */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(length = 255)
    private String phone;

    // ─── 알림 ───────────────────────────────────────────
    /** 휴대폰 푸시 알람 허용 여부. */
    @Column
    private Boolean pushOptIn;

    /** 브라우저/기기 푸시 토큰 (발급된 경우에만). */
    @Column(length = 255)
    private String pushToken;

    // ─── 약관 ───────────────────────────────────────────
    @Column
    private Boolean consentAgreed;

    // ─── 관리 정보 ──────────────────────────────────────
    /** 소프트 삭제(탈퇴) 시각. NULL 이면 정상 회원. */
    @Column
    private LocalDateTime deletedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime lastLoginAt;

    /**
     * 관리자가 비밀번호를 마지막으로 초기화해 준 시각.
     * 손님이 아직 임시 비밀번호를 쓰고 있는지 관리자 화면에서 알아보려고 남긴다.
     * 손님이 스스로 비밀번호를 바꾸면 다시 비운다.
     */
    @Column
    private LocalDateTime passwordResetAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (pushOptIn == null) pushOptIn = Boolean.FALSE;
    }

    /** 아이디/비밀번호로도 로그인할 수 있는 계정인지. */
    @Transient
    public boolean hasPassword() {
        return loginId != null && !loginId.isBlank()
                && passwordHash != null && !passwordHash.isBlank();
    }

    /** 가입 필수 정보(닉네임·성명·생년월일·휴대폰번호)가 모두 채워졌는지. */
    @Transient
    public boolean isProfileComplete() {
        return name != null && !name.isBlank()
                && nickname != null && !nickname.isBlank()
                && birthDate != null
                && phone != null && !phone.isBlank();
    }

    /** 공개 화면에 쓸 이름. 닉네임이 없으면 '회원'으로 대체하고 실명은 절대 쓰지 않는다. */
    @Transient
    public String displayName() {
        return nickname == null || nickname.isBlank() ? "회원" : nickname;
    }

    /** 만 나이. */
    @Transient
    public int age() {
        if (birthDate == null) return 0;
        return Period.between(birthDate, LocalDate.now()).getYears();
    }

    /** 가입 가능 나이인지 (만 18세 이상). */
    public static boolean isEligibleAge(LocalDate birthDate) {
        if (birthDate == null) return false;
        return !birthDate.isAfter(LocalDate.now().minusYears(MIN_AGE));
    }
}
