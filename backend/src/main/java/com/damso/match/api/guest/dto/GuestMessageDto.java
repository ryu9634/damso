package com.damso.match.api.guest.dto;

import java.time.LocalDateTime;

public record GuestMessageDto(
        Long id,
        Long senderId,
        String senderNickname,
        String senderMbti,
        Long receiverId,
        String receiverNickname,
        String content,
        String senderContact,
        LocalDateTime createdAt
) {}
