package com.damso.match.domain.dating.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 관리자용 매칭 페어.
 *  - 쌍방 찜이 발생하면 자동으로 생성되고(동기화), 관리자가 직접 만들 수도 있다.
 *  - 페어는 무순서이므로 항상 profileAId &lt; profileBId 로 정규화해 저장한다.
 *  - 찜을 취소해도 진행 이력은 남긴다(자동 삭제하지 않음).
 */
@Entity
@Table(
        name = "dating_matches",
        uniqueConstraints = @UniqueConstraint(columnNames = {"profileAId", "profileBId"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DatingMatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 작은 쪽 프로필 id. */
    @Column(nullable = false)
    private Long profileAId;

    /** 큰 쪽 프로필 id. */
    @Column(nullable = false)
    private Long profileBId;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private DatingMatchStage stage;

    /** 관리자 메모 (진행 상황 기록). */
    @Column(length = 500)
    private String memo;

    /** 쌍방 찜으로 자동 생성됐는지 (false = 관리자 수동 생성). */
    @Column
    private Boolean fromMutual;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (stage == null) stage = DatingMatchStage.NEW;
        if (updatedAt == null) updatedAt = createdAt;
    }
}