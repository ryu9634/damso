package com.damso.match.api.guest.dto;

import com.damso.match.global.common.Gender;

import java.util.List;

public record GuestSeatDto(
        boolean assigned,
        Integer seatNumber,
        List<Partner> partners
) {
    public record Partner(Long customerId, String nickname, String mbti, Gender gender) {}

    public static GuestSeatDto notAssigned() {
        return new GuestSeatDto(false, null, List.of());
    }
}
