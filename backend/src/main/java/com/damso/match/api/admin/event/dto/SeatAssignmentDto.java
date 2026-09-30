package com.damso.match.api.admin.event.dto;

import com.damso.match.domain.event.entity.SeatAssignment;
import com.damso.match.global.common.Gender;

public record SeatAssignmentDto(
        Long id,
        Long eventId,
        Integer round,
        Integer seatNumber,
        Integer slotIndex,
        Gender gender,
        Long customerId,
        String customerNickname,
        String customerMbti
) {
    public static SeatAssignmentDto of(SeatAssignment s, String nickname, String mbti) {
        return new SeatAssignmentDto(
                s.getId(), s.getEventId(), s.getRound(), s.getSeatNumber(),
                s.getSlotIndex(),
                s.getGender(), s.getCustomerId(), nickname, mbti
        );
    }
}
