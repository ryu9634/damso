package com.damso.match.api.admin.event.dto;

import java.time.LocalDate;

/**
 * 기간 내 여러 이벤트를 합산한 통계.
 *  - 비율은 프론트에서 분자/분모로 계산 (0 분모 처리 편의).
 */
public record StatsDto(
        LocalDate from,
        LocalDate to,
        int eventCount,

        long totalCustomers,     // ACTIVE 손님 합계 (전송률 분모)

        // MBTI I/E 비율
        long mbtiI,
        long mbtiE,

        // 호감도 지목 매칭률 = preferenceMatched / preferenceParticipants
        long preferenceParticipants, // 지목을 제출한 인원 (분모)
        long preferenceMatched,      // 쌍방(서로 지목) 매칭에 속한 인원 (분자)

        // 메시지 전송률 = messageSenders / totalCustomers
        long messageSenders,         // 메시지를 1건 이상 보낸 인원

        // 메시지 쌍방 전송률 = mutualMessagePeople / messageSenders
        long mutualMessagePeople     // 서로에게 메시지를 보낸(쌍방) 인원
) {}
