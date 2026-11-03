package com.damso.match.domain.dating.entity;

import com.damso.match.domain.member.entity.AppUser;
import com.damso.match.global.common.Gender;
import com.damso.match.global.security.EncryptedStringConverter;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * "담소가 해주는 소개팅" 프로필.
 *  - 상시 풀(pool). 이벤트와 무관.
 *  - 이름/연락처는 관리자에게만 노출, 이성 프로필 열람 시에는 미노출.
 *  - 자동 매칭 없음. 손님이 이성 프로필을 보고 '찜'하면 관리자가 확인해 수동 매칭.
 */
@Entity
@Table(name = "dating_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DatingProfile {

    /** 본인이 노출을 꺼뒀을 때 보여줄 안내. */
    public static final String HIDDEN_NOTICE =
            "프로필 노출을 꺼둔 상태예요. 마이페이지에서 다시 켜면 이용할 수 있어요.";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 이 프로필을 소유한 앱 회원({@link AppUser}) id.
     *  - 로그인한 회원만 프로필을 만들 수 있으므로 신규 등록은 항상 채워진다.
     *  - 계정 도입 전에 등록된 프로필은 NULL 이며, 마이페이지에서 이름+연락처로 연결할 수 있다.
     */
    @Column
    private Long appUserId;

    // ─── 기본 정보 (이름/연락처는 미노출 · DB 에는 암호화 저장) ──
    @Convert(converter = EncryptedStringConverter.class)
    @Column(nullable = false, length = 255)
    private String name;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(nullable = false, length = 255)
    private String contact;

    @Column(nullable = false)
    private Integer birthYear;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Gender gender;

    // ─── 상세 정보 (레거시) ───────────────────────────────
    // 상세 항목은 커스텀 질문(DatingQuestion) + 답변(DatingAnswer)으로 이전됨.
    // 아래 컬럼은 기존 데이터 이전용으로만 남겨두며 신규 등록 시엔 채우지 않는다.
    /** 키 (cm). */
    @Column
    private Integer height;

    /** 몸무게 (kg). */
    @Column
    private Integer weight;

    @Column(length = 4)
    private String mbti;

    /** 내 성격. */
    @Column(length = 300)
    private String personality;

    /** 취미 및 특기. */
    @Column(length = 300)
    private String hobbies;

    /** 장점. */
    @Column(length = 300)
    private String strengths;

    /** 단점. */
    @Column(length = 300)
    private String weaknesses;

    /** 업로드된 사진 경로 (예: /uploads/abc.jpg). */
    @Column(length = 255)
    private String photoUrl;

    // ─── 개인정보 동의 ──────────────────────────────────
    @Column
    private Boolean consentAgreed;

    // ─── 관리 정보 (관리자 전용) ─────────────────────────
    /** 관리 상태. 기존 행은 NULL 일 수 있으므로 읽을 때 {@link DatingStatus#orDefault} 로 보정한다. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private DatingStatus status;

    /** 관리자 메모 (손님에게는 절대 노출하지 않음). */
    @Column(length = 500)
    private String adminMemo;

    /**
     * 매장에서 직접 얼굴을 확인한 시각. NULL 이면 미확인.
     * 확인된 회원의 카드에는 '매장 확인' 배지가 붙는다. (온라인 앱이 못 하는 담소만의 보증)
     */
    @Column
    private LocalDateTime verifiedAt;

    /**
     * 본인이 프로필 노출을 꺼둔 시각. NULL 이면 노출 중.
     * 꺼두면 남의 열람 목록에서 빠지고, 본인도 이성 프로필을 보거나 고를 수 없다.
     * (한쪽만 막으면 답이 오지 않을 상대에게 좋아요를 보내게 된다)
     */
    @Column
    private LocalDateTime hiddenAt;

    /** 소프트 삭제 시각. NULL 이면 정상 회원. */
    @Column
    private LocalDateTime deletedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (status == null) status = DatingStatus.PENDING;
    }

    /** 이성 프로필 열람 목록에 노출되는 회원인지. (삭제·심사중·제외·본인이 감춘 회원은 제외) */
    @Transient
    public boolean isActive() {
        return deletedAt == null && hiddenAt == null && DatingStatus.orDefault(status).isBrowsable();
    }

    /** 매장에서 직접 확인한 회원인지. */
    @Transient
    public boolean isVerified() {
        return verifiedAt != null;
    }

    /** 본인이 노출을 꺼뒀는지. */
    @Transient
    public boolean isHidden() {
        return hiddenAt != null;
    }
}
