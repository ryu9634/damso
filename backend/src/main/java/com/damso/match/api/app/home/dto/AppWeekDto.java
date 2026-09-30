package com.damso.match.api.app.home.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 앱 홈에 보여줄 '이번 주' 캘린더.
 *  - 월요일부터 일요일까지 7일치를 언제나 채워서 내려준다. (앱은 그대로 그리기만 하면 된다)
 *  - 가게 일정(행사·휴일)은 모두에게 같고, 만남 약속은 요청한 회원 본인 것만 담긴다.
 */
public record AppWeekDto(
        LocalDate weekStart,
        LocalDate weekEnd,
        List<Day> days
) {
    /** dayOfWeek: 1(월) ~ 7(일). */
    public record Day(LocalDate date, int dayOfWeek, boolean today, List<Item> items) {}

    public record Item(Kind kind, String title, String detail, String emoji) {}

    public enum Kind { EVENT, HOLIDAY, MEETING }
}
