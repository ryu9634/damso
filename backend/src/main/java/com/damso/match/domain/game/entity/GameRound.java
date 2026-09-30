package com.damso.match.domain.game.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 한 판(라운드).
 *  - 문항은 라운드를 만들 때 콘텐츠에서 골라 그대로 복사해 둔다. 관리자가 나중에 문항을
 *    고치거나 지워도 지난 판의 기록이 바뀌지 않게 하려는 것이다.
 *  - 랜덤 뽑기는 서버가 뽑은 결과를 winner 에 적고 바로 공개 상태로 만든다.
 *    누가 돌려도 같은 결과를 봐야 하므로 뽑기는 반드시 서버에서 한다.
 */
@Entity
@Table(name = "game_rounds")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GameRound {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    // 게임 종류는 앞으로도 늘어난다. CHECK 제약이 붙지 않도록 컬럼 타입을 직접 준다.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private GameType gameType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private GameRoundStatus status;

    /** 밸런스 게임의 선택지 A / 랜덤 뽑기의 벌칙 문구. */
    @Column(length = 200)
    private String optionA;

    /** 밸런스 게임의 선택지 B. */
    @Column(length = 200)
    private String optionB;

    /** 랜덤 뽑기에서 뽑힌 사람의 이름. */
    @Column(length = 20)
    private String winnerName;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime revealedAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (status == null) status = GameRoundStatus.OPEN;
    }

    @Transient
    public boolean isRevealed() {
        return status == GameRoundStatus.REVEALED;
    }
}
