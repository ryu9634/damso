package com.damso.match.domain.venue.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 가게 캘린더에 올리는 일정 한 건 (행사 · 휴일).
 *  - 관리자가 등록하고, 손님 앱 홈에는 '이번 주' 분량만 보인다.
 *  - endDay 가 비어 있으면 하루짜리다. 며칠에 걸친 일정은 endDay 까지 매일 표시된다.
 *  - 시간은 자유 문구(timeText)로 둔다. "19:00 시작", "오후 6시부터" 처럼 쓰는 편이
 *    한 시간 단위로 강제하는 것보다 실제 운영에 맞다.
 */
@Entity
@Table(name = "calendar_entries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CalendarEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CalendarEntryType type;

    @Column(nullable = false, length = 60)
    private String title;

    @Column(length = 200)
    private String description;

    @Column(nullable = false)
    private LocalDate startDay;

    /** 마지막 날. NULL 이면 하루짜리 일정. */
    @Column
    private LocalDate endDay;

    /** 시간 안내 문구. 하루 종일이면 비워 둔다. */
    @Column(length = 30)
    private String timeText;

    /** 표시용 이모지. 비어 있으면 종류별 기본값을 쓴다. */
    @Column(length = 16)
    private String emoji;

    @Column
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    /** 마지막 날 (하루짜리면 시작일과 같다). */
    @Transient
    public LocalDate lastDay() {
        return endDay == null || endDay.isBefore(startDay) ? startDay : endDay;
    }

    /** 이 날짜에 걸쳐 있는 일정인지. */
    @Transient
    public boolean covers(LocalDate day) {
        return !day.isBefore(startDay) && !day.isAfter(lastDay());
    }

    @Transient
    public String emojiOrDefault() {
        return emoji == null || emoji.isBlank()
                ? CalendarEntryType.orDefault(type).defaultEmoji()
                : emoji;
    }
}
