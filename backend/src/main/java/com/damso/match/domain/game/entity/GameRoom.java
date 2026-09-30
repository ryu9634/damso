package com.damso.match.domain.game.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 게임방.
 *  - 로그인한 회원이면 누구나 만들 수 있고, 인원 제한은 두지 않는다.
 *  - 네 자리 코드로 들어온다. 목록에서 바로 고를 수도 있다.
 *  - 방 상태를 DB 에 두는 것이 중요하다. 메모리에 두면 배포로 서버가 재시작될 때마다
 *    진행 중이던 방이 통째로 사라진다.
 */
@Entity
@Table(name = "game_rooms", uniqueConstraints = @UniqueConstraint(columnNames = "code"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GameRoom {

    /** 방이 저절로 닫히는 시간. 손님이 그냥 나가버린 방을 목록에 계속 두지 않으려는 것. */
    public static final int EXPIRE_HOURS = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 네 자리 숫자 코드. 전화로 불러주기 쉬우라고 숫자만 쓴다. */
    @Column(nullable = false, length = 8)
    private String code;

    @Column(length = 40)
    private String title;

    /** 방장(만든 사람). 나가면 남은 사람 중 먼저 들어온 사람에게 넘어간다. */
    @Column(nullable = false)
    private Long hostUserId;

    // 컬럼 타입을 직접 준다. 이러지 않으면 Hibernate 가 enum 값 목록으로 CHECK 제약을 만들어,
    // 나중에 값을 하나 추가할 때 기존 DB 에서 저장이 막힌다.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private GameRoomStatus status;

    /**
     * 마피아 진행을 맡은 사람. 먼저 자처한 한 명이 갖는다.
     * 사회자는 역할을 받지 않고 인원 계산에서도 빠진다. 비어 있으면 전원이 참가한다.
     */
    @Column
    private Long moderatorUserId;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime updatedAt;

    @Column
    private LocalDateTime closedAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (status == null) status = GameRoomStatus.WAITING;
    }

    /** 오래 방치된 방인지. (목록에서 빼고 입장도 막는다) */
    @Transient
    public boolean isExpired() {
        LocalDateTime base = updatedAt == null ? createdAt : updatedAt;
        return base != null && base.plusHours(EXPIRE_HOURS).isBefore(LocalDateTime.now());
    }

    @Transient
    public boolean isOpen() {
        return status != GameRoomStatus.CLOSED && !isExpired();
    }
}
