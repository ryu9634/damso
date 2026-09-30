package com.damso.match.domain.game.entity;

/**
 * 역할 게임에서 각자 받는 비밀 역할.
 *
 * <p>시민은 라이어와 마피아에서 하는 일이 다르다. 라이어 게임에는 밤이 없고 제시어를 아는 쪽이
 * 시민이므로, 설명은 게임 종류를 받아서 고른다.
 */
public enum PlayerRole {
    CITIZEN("시민",
            "밤에는 할 일이 없어요. 낮에 이야기하고 투표하세요.",
            "제시어를 알고 있어요. 이야기 속에서 라이어를 찾아보세요."),
    LIAR("라이어", null, "제시어를 몰라요. 아는 척하며 들키지 마세요!"),
    MAFIA("마피아", "밤에 한 명을 지목합니다. 동료와 눈을 맞추세요.", null),
    POLICE("경찰", "밤에 한 명을 조사해 마피아인지 확인합니다.", null),
    DOCTOR("의사", "밤에 한 명을 살립니다. 마피아가 노린 사람과 겹치면 살아나요.", null);

    private final String label;
    private final String mafiaHint;
    private final String liarHint;

    PlayerRole(String label, String mafiaHint, String liarHint) {
        this.label = label;
        this.mafiaHint = mafiaHint;
        this.liarHint = liarHint;
    }

    public String label() {
        return label;
    }

    /** 역할 카드에 함께 보여줄 한 줄 설명. 게임에 맞는 문구를 고른다. */
    public String hint(GameType type) {
        String hint = (type == GameType.LIAR) ? liarHint : mafiaHint;
        return hint != null ? hint : (liarHint != null ? liarHint : mafiaHint);
    }
}
