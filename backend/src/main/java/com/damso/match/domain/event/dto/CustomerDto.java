package com.damso.match.domain.event.dto;

import com.damso.match.domain.event.entity.Customer;
import com.damso.match.domain.event.entity.CustomerStatus;
import com.damso.match.global.common.Gender;

import java.time.LocalDateTime;

public record CustomerDto(
        Long id,
        Long eventId,
        String nickname,
        String realName,
        String phone,
        Gender gender,
        String mbti,
        String valuesAnswers,
        Integer seatNumber,
        CustomerStatus status,
        LocalDateTime createdAt
) {
    public static CustomerDto from(Customer c) {
        return new CustomerDto(
                c.getId(), c.getEventId(), c.getNickname(), c.getRealName(),
                c.getPhone(), c.getGender(), c.getMbti(),
                c.getValuesAnswers(),
                c.getSeatNumber(),
                c.getStatus() == null ? CustomerStatus.ACTIVE : c.getStatus(),
                c.getCreatedAt()
        );
    }
}
