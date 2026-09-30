package com.damso.match.domain.game.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 게임방에 들어온 사람.
 *  - 한 방에 같은 회원은 한 줄만 있다. 나갔다 다시 들어오면 그 줄을 되살린다.
 *  - lastSeenAt 은 화면이 상태를 물어볼 때마다 갱신한다. 앱을 그냥 닫아버린 사람을
 *    '접속 중'으로 계속 세지 않으려면 이 값이 필요하다.
 */
@Entity
@Table(
        name = "game_players",
        uniqueConstraints = @UniqueConstraint(columnNames = {"room_id", "user_id"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GamePlayer {

    /** 이 시간 안에 소식이 있으면 접속 중으로 본다. */
    public static final int ACTIVE_SECONDS = 90;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 방에 보이는 이름. 닉네임을 그대로 쓴다. */
    @Column(nullable = false, length = 20)
    private String displayName;

    @Column(nullable = false)
    private LocalDateTime joinedAt;

    @Column
    private LocalDateTime lastSeenAt;

    /** 스스로 나간 시각. NULL 이면 아직 방에 있다. */
    @Column
    private LocalDateTime leftAt;

    @PrePersist
    public void prePersist() {
        if (joinedAt == null) joinedAt = LocalDateTime.now();
        if (lastSeenAt == null) lastSeenAt = joinedAt;
    }

    @Transient
    public boolean isActive() {
        return leftAt == null
                && lastSeenAt != null
                && lastSeenAt.plusSeconds(ACTIVE_SECONDS).isAfter(LocalDateTime.now());
    }
}
