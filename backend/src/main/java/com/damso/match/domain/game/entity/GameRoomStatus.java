package com.damso.match.domain.game.entity;

/** 게임방 상태. */
public enum GameRoomStatus {
    /** 사람을 기다리는 중 (게임 사이의 대기도 여기) */
    WAITING("대기 중"),
    /** 진행 중인 라운드가 있음 */
    PLAYING("게임 중"),
    /** 끝난 방. 다시 들어갈 수 없다. */
    CLOSED("종료");

    private final String label;

    GameRoomStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
