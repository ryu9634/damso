package com.damso.match.domain.game.entity;

import com.damso.match.domain.setting.service.SettingsCatalog;

/**
 * 게임방에서 할 수 있는 게임.
 *  - 관리자가 종류별로 켜고 끌 수 있다. (SettingsCatalog 의 설정 키와 짝을 이룬다)
 *  - contentType: 이 게임이 쓰는 콘텐츠 종류. 랜덤 뽑기는 벌칙 문구가 없어도 돌아간다.
 */
public enum GameType {
    /** 두 선택지 중 하나를 고르고 분포를 함께 본다. */
    BALANCE("밸런스 게임", "둘 중 하나를 고르고, 다 같이 결과를 봐요"),
    /** 방에 있는 사람 중 한 명을 서버가 뽑는다. 벌칙 문구가 있으면 함께 보여준다. */
    PICK("랜덤 뽑기", "누가 걸릴까요? 벌칙도 함께 뽑아드려요"),
    /** 한 명만 제시어를 모른다. 이야기해보고 라이어를 찾는다. */
    LIAR("라이어 게임", "한 명만 제시어를 몰라요. 이야기하고 찾아내세요", 3),
    /** 마피아와 시민으로 나뉜다. 이야기한 뒤 한 명을 지목한다. */
    MAFIA("마피아", "역할을 나눠 받고, 이야기한 뒤 한 명을 지목해요", 4);

    private final String label;
    private final String description;
    /** 이 인원은 있어야 게임이 성립한다. */
    private final int minPlayers;

    GameType(String label, String description) {
        this(label, description, 1);
    }

    GameType(String label, String description, int minPlayers) {
        this.label = label;
        this.description = description;
        this.minPlayers = minPlayers;
    }

    public int minPlayers() {
        return minPlayers;
    }

    /** 역할을 나눠 갖고 투표로 끝나는 게임인지. 라이어·마피아가 같은 흐름을 쓴다. */
    public boolean isRoleGame() {
        return this == LIAR || this == MAFIA;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }
}
