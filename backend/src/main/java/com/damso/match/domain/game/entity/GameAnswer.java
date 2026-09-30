package com.damso.match.domain.game.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 라운드에 낸 답. 한 사람이 한 라운드에 하나만 낸다.
 * 공개 전이라면 마음을 바꿔도 되므로, 새로 내면 기존 답을 고친다.
 */
@Entity
@Table(
        name = "game_answers",
        uniqueConstraints = @UniqueConstraint(columnNames = {"round_id", "user_id"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GameAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "round_id", nullable = false)
    private Long roundId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * 밸런스 게임은 "A" 또는 "B".
     * value 는 DB 예약어라 컬럼 이름을 따로 준다. (그대로 두면 테이블이 만들어지지 않는다)
     */
    @Column(name = "answer_value", nullable = false, length = 10)
    private String value;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
