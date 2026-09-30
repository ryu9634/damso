package com.damso.match.domain.dating.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * "손님 선택 소개팅" 프로필의 상세 질문(관리자 커스텀).
 *  - 관리자가 자유롭게 추가/수정/삭제/정렬한다. (현장 '설문 문항'과 동일한 개념)
 *  - 손님 가입 화면은 이 질문 목록을 받아 동적으로 입력칸을 그린다.
 *  - 답변은 {@link DatingAnswer} 에 (프로필, 질문, 값) 으로 저장된다.
 */
@Entity
@Table(name = "dating_questions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DatingQuestion {

    /** 입력 유형. */
    public enum QType { TEXT, TEXTAREA, NUMBER, SELECT }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Integer sortOrder;

    /** 질문 제목 (예: "키 (cm)", "MBTI"). */
    @Column(nullable = false, length = 100)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QType type;

    /** SELECT 유형의 선택지. 줄바꿈으로 구분. 그 외 유형은 null. */
    @Column(length = 2000)
    private String options;

    /** 필수 입력 여부. */
    @Column(nullable = false)
    private boolean required;
}