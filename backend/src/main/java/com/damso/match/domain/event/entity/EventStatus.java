package com.damso.match.domain.event.entity;

/**
 * 담소 소개팅 진행 단계.
 *
 * <p>자리는 네 번 바꾼다. 2차에서 제일 잘 맞는 사람과 앉아보고, 3차에서는 반대로
 * 제일 안 맞게 나온 사람과 앉아본다. (점수는 점수일 뿐이라 직접 만나보면 다르다)
 * 그 뒤에 호감도를 받고, 마지막 4차에서 지목한 사람 옆으로 앉힌다.
 */
public enum EventStatus {
    REGISTRATION,    // 손님 입력 단계
    ROUND_1,         // 1차 자리 (입장 순서) 진행 중
    ROUND_2,         // 2차 자리 (점수 높은 순) 진행 중
    ROUND_3,         // 3차 자리 (점수 낮은 순 — 가장 안 맞는 상대) 진행 중
    PREFERENCE,      // 호감도 지목 단계
    ROUND_4,         // 4차 자리 (지목 기반) 진행 중
    MESSAGE,         // 최종 메시지 단계
    CLOSED           // 종료
}
