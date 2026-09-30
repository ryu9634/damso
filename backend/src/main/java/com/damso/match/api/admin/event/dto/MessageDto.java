package com.damso.match.api.admin.event.dto;

import java.time.LocalDateTime;

public record MessageDto(
        Long id,
        Long senderId,
        String senderNickname,
        Long receiverId,
        String receiverNickname,
        String content,
        String senderContact,
        LocalDateTime createdAt
) {}
