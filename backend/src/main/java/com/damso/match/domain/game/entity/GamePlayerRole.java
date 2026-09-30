package com.damso.match.domain.game.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * 한 라운드에서 각자가 받은 비밀 역할.
 *  - 서버는 요청한 본인의 역할만 내려준다. 남의 역할을 함께 보내면 게임이 성립하지 않는다.
 *  - 마피아는 예외적으로 동료 마피아까지 볼 수 있다. (게임 규칙이 그렇다)
 */
@Entity
@Table(
        name = "game_player_roles",
        uniqueConstraints = @UniqueConstraint(columnNames = {"round_id", "user_id"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GamePlayerRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "round_id", nullable = false)
    private Long roundId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private PlayerRole role;
}
