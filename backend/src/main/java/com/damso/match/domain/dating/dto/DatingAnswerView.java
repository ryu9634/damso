package com.damso.match.domain.dating.dto;

/** 프로필 표시용 답변 한 줄 (질문 라벨 + 값). */
public record DatingAnswerView(
        Long questionId,
        String label,
        String value
) {}