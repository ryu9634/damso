package com.damso.match.api.guest.dto;

import com.damso.match.global.common.Gender;

public record GuestSeatViewDto(
        Integer seatNumber,
        Integer slotIndex,
        Gender gender,
        Long customerId,
        String nickname
) {}