package com.damso.match.api.app.dating.dto;

import com.damso.match.domain.dating.entity.DatingPairNote;
import com.damso.match.domain.dating.entity.MeetingStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 서로 좋아요가 된 두 사람의 만남 약속 (손님 화면용).
 *  - 제안하는 쪽이 '날짜+시간' 후보를 최대 3개까지 고르고, 상대가 그중 하나를 골라 확정한다.
 *    확정 전에는 dateHour 가 비어 있고 slots 에 후보가 담긴다.
 *  - 장소는 관리자가 정해 둔 곳으로 고정이라 손님이 입력하지 않는다.
 *  - 관리자 메모(memo)는 담지 않는다. 손님에게 보이는 건 약속 정보뿐이다.
 */
public record DatingMeetingDto(
        Long otherProfileId,
        MeetingStatus status,
        /** 확정된 날짜. 제안 중이면 후보 가운데 가장 이른 날. */
        LocalDate dateDay,
        /** 확정된 시각. 상대가 고르기 전이면 null. */
        Integer dateHour,
        /** 제안한 사람이 고른 후보 (날짜+시간). */
        List<Slot> slots,
        String place,
        boolean proposedByMe,
        LocalDateTime updatedAt
) {
    /** 만날 날짜와 시각 한 벌. */
    public record Slot(LocalDate day, Integer hour) {}

    /** 아직 아무도 제안하지 않은 상태. */
    public static DatingMeetingDto empty(Long otherProfileId) {
        return new DatingMeetingDto(otherProfileId, MeetingStatus.NONE,
                null, null, List.of(), null, false, null);
    }

    public static DatingMeetingDto from(DatingPairNote n, Long meProfileId) {
        if (n == null) return null;
        Long other = n.getProfileAId().equals(meProfileId) ? n.getProfileBId() : n.getProfileAId();
        return new DatingMeetingDto(
                other,
                n.statusOrDefault(),
                n.getDateDay(),
                n.getDateHour(),
                n.preferredSlotList().stream()
                        .map(s -> new Slot(s.day(), s.hour()))
                        .toList(),
                n.getPlace(),
                meProfileId.equals(n.getProposedByProfileId()),
                n.getUpdatedAt());
    }

    /** 약속 제안 요청 — 날짜+시간 후보 최대 3개. */
    public record ProposeRequest(List<Slot> slots) {}

    /** 상대가 고른 후보로 확정. 후보가 하나뿐이면 비워도 된다. */
    public record ConfirmRequest(LocalDate day, Integer hour) {}
}
