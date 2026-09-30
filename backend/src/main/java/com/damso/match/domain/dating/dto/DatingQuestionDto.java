package com.damso.match.domain.dating.dto;

import com.damso.match.domain.dating.entity.DatingQuestion;

import java.util.Arrays;
import java.util.List;

/** 소개팅 상세 질문 (손님 폼 렌더링 + 관리자 목록 공용). */
public record DatingQuestionDto(
        Long id,
        Integer sortOrder,
        String label,
        String type,
        List<String> options,
        boolean required
) {
    public static DatingQuestionDto from(DatingQuestion q) {
        List<String> opts = (q.getOptions() == null || q.getOptions().isBlank())
                ? List.of()
                : Arrays.stream(q.getOptions().split("\n"))
                    .map(String::trim).filter(s -> !s.isEmpty()).toList();
        return new DatingQuestionDto(
                q.getId(), q.getSortOrder(), q.getLabel(),
                q.getType().name(), opts, q.isRequired()
        );
    }
}