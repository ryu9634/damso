package com.damso.match.api.admin.event.dto;

import com.damso.match.domain.event.entity.EventStatus;

public record DashboardDto(
        Long eventId,
        String eventName,
        EventStatus status,
        long totalCustomers,
        long maleCount,
        long femaleCount,
        long round1Assigned,
        long round2Assigned,
        long round3Assigned,
        long round4Assigned,
        long preferenceSubmitted,
        long messageCount
) {}
