package com.damso.match.api.admin.dating.dto;

import com.damso.match.domain.dating.entity.MeetingStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 페어(두 회원) 메모 + 데이트 약속. 캘린더/목록 표시용.
 *  - dateDay/dateHour 가 비어 있으면 '약속 미정'(메모만 있는 상태)이다.
 *  - place/status 는 손님이 앱에서 직접 잡은 약속에서 채워진다.
 */
public record DatingPairNoteDto(
        Long id,
        Long aId,
        String aName,
        Long bId,
        String bName,
        String memo,
        LocalDate dateDay,
        Integer dateHour,
        LocalDateTime updatedAt,
        String place,
        MeetingStatus status
) {}
