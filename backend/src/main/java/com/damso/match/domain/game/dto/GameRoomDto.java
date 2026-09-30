package com.damso.match.domain.game.dto;

import java.time.LocalDateTime;

/** 게임방 목록 한 줄. */
public record GameRoomDto(
        String code,
        String title,
        String hostName,
        String status,
        String statusLabel,
        int playerCount,
        boolean joined,
        LocalDateTime createdAt
) {}
