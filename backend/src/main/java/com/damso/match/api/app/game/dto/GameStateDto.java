package com.damso.match.api.app.game.dto;

import com.damso.match.domain.game.entity.GameType;

import java.util.List;

/**
 * 게임방 화면이 통째로 그리는 데 필요한 상태.
 *  - 앱은 이 하나를 주기적으로 다시 불러 화면을 갱신한다. (지금은 2초 간격 폴링)
 *  - 결과는 공개(REVEALED) 전까지 내려보내지 않는다. 미리 보면 게임이 성립하지 않는다.
 */
public record GameStateDto(
        String code,
        String title,
        String status,
        String statusLabel,
        String hostName,
        /** 내가 방장인지 — 게임 시작·공개·방 종료 버튼을 이 값으로 가른다. */
        boolean host,
        /** 마피아 진행을 맡은 사람. 없으면 null */
        String moderatorName,
        /** 내가 사회자인지 */
        boolean moderator,
        List<Player> players,
        int activeCount,
        Round round,
        /** 지금 시작할 수 있는 게임 (관리자가 켜 둔 것만) */
        List<GameOption> games
) {
    public record Player(Long userId, String name, boolean active, boolean host,
                         boolean moderator, boolean me) {}

    public record GameOption(GameType type, String label, String description) {}

    public record Round(
            Long id,
            GameType type,
            String typeLabel,
            /** OPEN / ROLE / VOTE / REVEALED */
            String phase,
            String phaseLabel,
            boolean revealed,
            String optionA,
            String optionB,
            /** 랜덤 뽑기에서 뽑힌 사람 */
            String winnerName,
            /** 내가 고른 값 (A/B) 또는 내가 지목한 사람의 id. 아직이면 null */
            String myAnswer,
            int answered,
            /** 아래 집계는 공개된 뒤에만 채워진다. */
            int countA,
            int countB,
            List<String> namesA,
            List<String> namesB,
            /** ─── 역할 게임(라이어·마피아) ─── */
            /** 내 역할. 남의 역할은 공개 전까지 내려보내지 않는다. */
            String myRole,
            String myRoleLabel,
            /** 라이어 게임의 제시어. 라이어에게는 내려보내지 않는다. */
            String keyword,
            String category,
            /** 역할 카드에 함께 보여줄 한 줄 설명 */
            String myRoleHint,
            /** 마피아일 때만 채워지는 동료 목록 */
            List<String> mates,
            /** 사회자에게만 내려가는 전체 역할표 (마피아 전용) */
            List<RoleCard> allRoles,
            /** 이번 판에 역할을 받은 사람들. 투표 후보이자 투표할 수 있는 사람이다. */
            List<Long> playerIds,
            /** 공개 뒤의 득표 집계 */
            List<Vote> votes,
            /** 공개 뒤에 밝혀지는 라이어·마피아 */
            List<String> revealedRoles,
            /** 공개 뒤 한 줄 결과 */
            String outcome
    ) {}

    /** 누가 몇 표 받았는지. */
    public record Vote(String name, int count, List<String> voters) {}

    /** 사회자 진행용 역할표 한 줄. */
    public record RoleCard(String name, String role, String roleLabel) {}
}
