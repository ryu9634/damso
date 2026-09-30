package com.damso.match.api.app.dating.dto;

import jakarta.validation.constraints.*;

import java.util.List;

/**
 * 본인 프로필 수정 요청 (로그인한 손님이 직접 고치는 경우).
 *  - 성별은 이미 맺어진 찜 관계가 꼬이므로 바꿀 수 없다. 서버가 기존 값을 유지한다.
 *  - answers 는 전체 교체다. 보내지 않은 질문의 기존 답변은 지워진다.
 */
public record DatingSelfUpdateRequest(
        @NotBlank @Size(max = 30, message = "이름은 최대 30자입니다") String name,
        @NotBlank @Size(max = 30, message = "연락처를 입력해주세요") String contact,
        @NotNull @Min(value = 1940, message = "출생년도를 확인해주세요") @Max(2012) Integer birthYear,
        String photoUrl,
        List<DatingProfileCreateRequest.AnswerInput> answers
) {}
