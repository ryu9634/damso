package com.damso.match.domain.dating.entity;

/**
 * 두 사람의 만남 약속 상태.
 *  - NONE      : 아직 아무도 제안하지 않음 (관리자 메모만 있는 상태 포함)
 *  - PROPOSED  : 한 사람이 날짜·시간을 제안하고 상대 확인을 기다리는 중
 *  - CONFIRMED : 두 사람이 확정 (관리자가 직접 잡아준 약속도 여기)
 */
public enum MeetingStatus {
    NONE("미정"),
    PROPOSED("제안됨"),
    CONFIRMED("확정");

    private final String label;

    MeetingStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static MeetingStatus orDefault(MeetingStatus s) {
        return s == null ? NONE : s;
    }
}
