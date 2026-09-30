package com.damso.match.domain.event.entity;

import com.damso.match.domain.member.entity.AppUser;
import com.damso.match.global.common.Gender;
import com.damso.match.global.security.EncryptedStringConverter;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "customers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long eventId;

    /**
     * 이 손님이 로그인한 앱 회원({@link AppUser}) id.
     * 계정 도입 전 등록분은 NULL 이라 본명+연락처 재로그인 경로도 남겨둔다.
     */
    @Column
    private Long appUserId;

    @Column(nullable = false, length = 50)
    private String nickname;

    /** 실명·휴대폰번호는 DB 에 암호화해서 둔다. 화면에는 풀어서 보인다. */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(nullable = false, length = 255)
    private String realName;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(nullable = false, length = 255)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Gender gender;

    @Column(nullable = false, length = 4)
    private String mbti;

    /**
     * 가치관 문항 답변 (각 0 또는 1).
     * 현재 활성 문항 수만큼의 길이. 예: "0101010101010101"
     */
    @Column(nullable = false, length = 100)
    private String valuesAnswers;

    /** 손님이 입장 시 받은 자리 번호. 1차 배치는 이 값 기준. */
    @Column
    private Integer seatNumber;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private CustomerStatus status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (status == null) status = CustomerStatus.ACTIVE;
    }

    @PostLoad
    public void postLoad() {
        if (status == null) status = CustomerStatus.ACTIVE;
    }
}
