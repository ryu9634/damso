package com.damso.match.api.admin.venue.dto;

import com.damso.match.domain.venue.entity.CalendarEntry;
import com.damso.match.domain.venue.entity.CalendarEntryType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** 캘린더 일정 한 건 (관리자 화면용). */
public record CalendarEntryDto(
        Long id,
        CalendarEntryType type,
        String typeLabel,
        String title,
        String description,
        LocalDate startDay,
        LocalDate endDay,
        String timeText,
        String emoji
) {
    public static CalendarEntryDto from(CalendarEntry c) {
        CalendarEntryType type = CalendarEntryType.orDefault(c.getType());
        return new CalendarEntryDto(
                c.getId(), type, type.label(), c.getTitle(), c.getDescription(),
                c.getStartDay(), c.getEndDay(), c.getTimeText(), c.emojiOrDefault());
    }

    /** 등록 · 수정 요청. endDay 를 비우면 하루짜리 일정이 된다. */
    public record SaveRequest(
            @NotNull(message = "종류를 골라주세요") CalendarEntryType type,
            @NotBlank(message = "제목을 입력해주세요")
            @Size(max = 60, message = "제목은 최대 60자입니다") String title,
            @Size(max = 200, message = "설명은 최대 200자입니다") String description,
            @NotNull(message = "날짜를 골라주세요") LocalDate startDay,
            LocalDate endDay,
            @Size(max = 30, message = "시간 문구는 최대 30자입니다") String timeText,
            @Size(max = 16) String emoji
    ) {}
}
