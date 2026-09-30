package com.damso.match.domain.dating.service;

import com.damso.match.domain.event.entity.Customer;
import com.damso.match.domain.event.repository.ValueQuestionRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 남녀 회원 간 1:1 매칭 점수 계산기.
 *  - MBTI 궁합 (20점 만점, 별도 매트릭스)
 *  - 가치관 일치 (현재 활성 문항 수에 비례, 80점 만점)
 *  - 총합 100점 만점
 */
@Service
@RequiredArgsConstructor
public class MatchScoreService {

    private final ValueQuestionRepository questionRepo;

    private static final int VALUES_MAX = 80;
    /** MBTI 매트릭스에 없는 경우 fallback 점수. */
    private static final int MBTI_FALLBACK = 10;

    /** 행/열 순서. row[i] 가 row 사람의 MBTI, col[j] 가 상대 MBTI. */
    private static final String[] MBTI_ORDER = {
            "INFP", "ENFP", "INFJ", "ENFJ",
            "INTJ", "ENTJ", "INTP", "ENTP",
            "ISFP", "ESFP", "ISTP", "ESTP",
            "ISFJ", "ESFJ", "ISTJ", "ESTJ"
    };

    /**
     * 16x16 MBTI 궁합 점수표.
     *  0  = 진짜 궁합 최악
     *  5  = 최악은 면했지만 그닥
     *  10 = 안 맞는 것과 맞는 것 반반
     *  15 = 아주 좋은 관계
     *  20 = 천생연분
     */
    private static final int[][] MBTI_MATRIX = {
            //          INFP ENFP INFJ ENFJ INTJ ENTJ INTP ENTP ISFP ESFP ISTP ESTP ISFJ ESFJ ISTJ ESTJ
            /* INFP */ { 15,  15,  15,  20,  15,  20,  15,  15,   0,   0,   0,   0,   0,   0,   0,   0 },
            /* ENFP */ { 15,  15,  20,  15,  20,  15,  15,  15,   0,   0,   0,   0,   0,   0,   0,   0 },
            /* INFJ */ { 15,  20,  15,  15,  15,  15,  15,  20,   0,   0,   0,   0,   0,   0,   0,   0 },
            /* ENFJ */ { 20,  15,  15,  15,  15,  15,  15,  15,  20,   0,   0,   0,   0,   0,   0,   0 },
            /* INTJ */ { 15,  20,  15,  15,  15,  15,  15,  20,  10,  10,  10,  10,   5,   5,   5,   5 },
            /* ENTJ */ { 20,  15,  15,  15,  15,  15,  20,  15,  10,  10,  10,  10,  10,  10,  10,  10 },
            /* INTP */ { 15,  15,  15,  15,  15,  20,  15,  15,  10,  10,  10,  10,   5,   5,   5,  20 },
            /* ENTP */ { 15,  15,  20,  15,  20,  15,  15,  15,  10,  10,  10,  10,   5,   5,   5,   5 },
            /* ISFP */ {  0,   0,   0,  20,  10,  10,  10,  10,   5,   5,   5,   5,  10,  20,  10,  20 },
            /* ESFP */ {  0,   0,   0,   0,  10,  10,  10,  10,   5,   5,   5,   5,  20,  10,  20,  10 },
            /* ISTP */ {  0,   0,   0,   0,  10,  10,  10,  10,   5,   5,   5,   5,  10,  20,  10,  20 },
            /* ESTP */ {  0,   0,   0,   0,  10,  10,  10,  10,   5,   5,   5,   5,  20,  10,  20,  10 },
            /* ISFJ */ {  0,   0,   0,   0,   5,  10,   5,   5,  10,  20,  10,  20,  15,  15,  15,  15 },
            /* ESFJ */ {  0,   0,   0,   0,   5,  10,   5,   5,  20,  10,  20,  10,  15,  15,  15,  15 },
            /* ISTJ */ {  0,   0,   0,   0,   5,  10,   5,   5,  10,  20,  10,  20,  15,  15,  15,  15 },
            /* ESTJ */ {  0,   0,   0,   0,   5,  10,  20,   5,  20,  10,  20,  10,  15,  15,  15,  15 },
    };

    public int totalScore(Customer a, Customer b) {
        return mbtiScore(a.getMbti(), b.getMbti()) + valuesScore(a.getValuesAnswers(), b.getValuesAnswers());
    }

    public int mbtiScore(String m1, String m2) {
        if (m1 == null || m2 == null) return MBTI_FALLBACK;
        int i = mbtiIndex(m1.toUpperCase());
        int j = mbtiIndex(m2.toUpperCase());
        if (i < 0 || j < 0) return MBTI_FALLBACK;
        return MBTI_MATRIX[i][j];
    }

    public int valuesScore(String a, String b) {
        if (a == null || b == null) return 0;
        int qCount = (int) questionRepo.count();
        if (qCount <= 0) return 0;
        int compareLen = Math.min(Math.min(a.length(), b.length()), qCount);
        if (compareLen <= 0) return 0;
        int match = 0;
        for (int i = 0; i < compareLen; i++) {
            if (a.charAt(i) == b.charAt(i)) match++;
        }
        return (int) Math.round(((double) VALUES_MAX * match) / qCount);
    }

    private int mbtiIndex(String mbti) {
        for (int i = 0; i < MBTI_ORDER.length; i++) {
            if (MBTI_ORDER[i].equals(mbti)) return i;
        }
        return -1;
    }
}
