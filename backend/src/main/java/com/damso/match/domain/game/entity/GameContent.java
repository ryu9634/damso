package com.damso.match.domain.game.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 게임에 쓰는 문항 묶음. 관리자가 등록한다.
 *  - BALANCE: optionA / optionB 두 선택지
 *  - PICK: optionA 에 벌칙·미션 문구 하나 (없으면 이름만 뽑는다)
 * visible=false 면 뽑기 대상에서 빠진다. 지우지 않고 잠깐 내려둘 때 쓴다.
 */
@Entity
@Table(name = "game_contents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GameContent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private GameType type;

    @Column(nullable = false, length = 200)
    private String optionA;

    @Column(length = 200)
    private String optionB;

    @Column
    private Boolean visible;

    @Column
    private Integer sortOrder;

    @Column
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (visible == null) visible = Boolean.TRUE;
        if (sortOrder == null) sortOrder = 0;
    }

    @Transient
    public boolean isVisibleItem() {
        return !Boolean.FALSE.equals(visible);
    }
}
