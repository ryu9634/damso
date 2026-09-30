package com.damso.match.api.guest.dto;

import com.damso.match.global.common.Gender;

public record GuestCandidateDto(
        Long id,
        String nickname,
        String mbti,
        Gender gender
) {}
