package com.damso.match.domain.dating.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * 소개팅 프로필의 질문별 답변.
 *  - (profileId, questionId) 당 한 건.
 *  - 질문/프로필 삭제 시 함께 정리된다.
 */
@Entity
@Table(name = "dating_answers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DatingAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long profileId;

    @Column(nullable = false)
    private Long questionId;

    /** 'value' 는 SQL 예약어라 컬럼명을 분리한다. */
    @Column(name = "answer_value", length = 500)
    private String value;
}