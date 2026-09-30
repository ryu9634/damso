package com.damso.match.api.app.dating.dto;

import com.damso.match.domain.dating.entity.DatingQuestion;
import com.damso.match.global.common.Gender;

import jakarta.validation.constraints.*;

import java.util.List;

/**
 * 소개팅 프로필 등록 요청.
 *  - 식별 정보는 고정 필드로 받는다.
 *  - 상세 항목은 관리자 커스텀 질문(DatingQuestion)에 대한 답변 목록으로 받는다.
 */
public record DatingProfileCreateRequest(
        // ── 기본(식별) 정보 ──
        @NotBlank @Size(max = 30, message = "이름은 최대 30자입니다") String name,
        @NotBlank @Size(max = 30, message = "연락처를 입력해주세요") String contact,
        @NotNull @Min(value = 1940, message = "출생년도를 확인해주세요") @Max(2012) Integer birthYear,
        @NotNull Gender gender,
        String photoUrl,
        // ── 상세 질문 답변 ──
        List<AnswerInput> answers,
        // ── 개인정보 동의 ──
        @NotNull @AssertTrue(message = "개인정보 수집·이용에 동의해야 합니다") Boolean agreedPrivacy
) {
    public record AnswerInput(Long questionId, String value) {}
}