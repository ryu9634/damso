package com.damso.match.api.admin.dating.dto;

import java.util.List;

public record MatchScoreMatrixDto(
        List<PersonBrief> males,
        List<PersonBrief> females,
        /** maleIdx → femaleIdx → MBTI 궁합 점수 (0~20) */
        int[][] mbtiMatrix,
        /** maleIdx → femaleIdx → 가치관 일치 점수 (0~80) */
        int[][] valuesMatrix,
        /** maleIdx → femaleIdx → 총합 (0~100) */
        int[][] totalMatrix
) {
    public record PersonBrief(Long id, String nickname, String mbti) {}
}
