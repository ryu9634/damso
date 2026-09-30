package com.damso.match.domain.venue.entity;

/** 캘린더에 올리는 일정의 종류. */
public enum CalendarEntryType {
    EVENT("행사", "🎉"),
    HOLIDAY("휴일", "🌙");

    private final String label;
    private final String defaultEmoji;

    CalendarEntryType(String label, String defaultEmoji) {
        this.label = label;
        this.defaultEmoji = defaultEmoji;
    }

    public String label() {
        return label;
    }

    /** 관리자가 이모지를 따로 안 골랐을 때 쓰는 기본 이모지. */
    public String defaultEmoji() {
        return defaultEmoji;
    }

    public static CalendarEntryType orDefault(CalendarEntryType t) {
        return t == null ? EVENT : t;
    }
}
