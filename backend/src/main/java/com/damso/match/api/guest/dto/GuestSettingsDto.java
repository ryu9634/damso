package com.damso.match.api.guest.dto;

/** 손님(모바일) 화면에서 쓰는 설정값. */
public record GuestSettingsDto(
        int maxMessageRecipients,
        String preferenceTitle,
        String preferenceSubtitle,
        String messageTitle,
        String messageSubtitle,
        String waitingTitle,
        String waitingSubtitle,
        String endTitle,
        String endSubtitle
) {}
