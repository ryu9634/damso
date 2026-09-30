package com.damso.match.api.admin.event.dto;

public record PreferenceDto(
        Long id,
        Long voterId,
        String voterNickname,
        Long firstChoiceId,
        String firstChoiceNickname,
        Long secondChoiceId,
        String secondChoiceNickname,
        Long thirdChoiceId,
        String thirdChoiceNickname
) {}
