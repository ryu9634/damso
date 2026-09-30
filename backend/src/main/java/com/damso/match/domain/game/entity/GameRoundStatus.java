package com.damso.match.domain.game.entity;

/**
 * 라운드 진행 단계.
 *  - 밸런스 게임: OPEN → REVEALED
 *  - 라이어·마피아: ROLE(각자 역할 확인) → VOTE(지목) → REVEALED
 *  - 랜덤 뽑기: 만들자마자 REVEALED
 */
public enum GameRoundStatus {
    /** 답을 받는 중 */
    OPEN("고르는 중"),
    /** 각자 자기 역할을 확인하고 이야기하는 중 */
    ROLE("역할 확인"),
    /** 지목(투표) 중 */
    VOTE("투표 중"),
    /** 결과 공개됨 */
    REVEALED("결과 공개");

    private final String label;

    GameRoundStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
