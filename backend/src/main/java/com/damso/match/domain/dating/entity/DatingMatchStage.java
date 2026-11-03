package com.damso.match.domain.dating.entity;

/**
 * 매칭 페어의 진행 단계 (관리자 워크플로우).
 * NEW → CONTACT_SHARED → MEETING_SET → SUCCESS / FAILED
 */
public enum DatingMatchStage {
    /** 쌍방 찜 발생 — 아직 아무 조치 없음. */
    NEW,
    /** 양쪽에 연락처 전달 완료. */
    CONTACT_SHARED,
    /** 만남 날짜 확정. */
    MEETING_SET,
    /** 성사. */
    SUCCESS,
    /** 불발. */
    FAILED;

    public static DatingMatchStage orDefault(DatingMatchStage s) {
        return s == null ? NEW : s;
    }}