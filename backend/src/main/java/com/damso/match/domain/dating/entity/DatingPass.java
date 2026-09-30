package com.damso.match.domain.dating.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * '관심 없음' 기록. fromProfile 이 toProfile 을 목록에서 치운 것.
 *  - 좋아요와 달리 상대에게 알리지 않는다. 조용히 내 목록에서만 빠진다.
 *  - 되돌리고 싶으면 손님이 '숨긴 프로필 다시 보기'로 한 번에 지운다.
 */
@Entity
@Table(
        name = "dating_passes",
        uniqueConstraints = @UniqueConstraint(columnNames = {"fromProfileId", "toProfileId"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DatingPass {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long fromProfileId;

    @Column(nullable = false)
    private Long toProfileId;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
