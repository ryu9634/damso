package com.damso.match.domain.dating.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 소개팅 '좋아요'. fromProfile 이 toProfile 에게 보낸 기록.
 * 같은 대상 중복 전송은 (from, to) 유니크로 방지.
 *
 * <p>받은 사람은 좋아요 / 미안해요 중 하나로 답한다({@link DatingLikeStatus}).
 * 좋아요로 답하면 두 사람은 만남 약속({@link DatingPairNote})을 잡을 수 있다.
 */
@Entity
@Table(
        name = "dating_likes",
        uniqueConstraints = @UniqueConstraint(columnNames = {"fromProfileId", "toProfileId"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DatingLike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long fromProfileId;

    @Column(nullable = false)
    private Long toProfileId;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    /**
     * 받은 사람의 답. 기존 데이터는 NULL 이라 읽을 때 PENDING 으로 본다.
     * columnDefinition 을 직접 준 이유는 값이 늘어날 때 CHECK 제약에 걸리지 않게 하기 위해서다.
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 20, columnDefinition = "varchar(20)")
    private DatingLikeStatus status;

    /** 답한 시각. 아직 답 전이면 NULL. */
    @Column
    private LocalDateTime respondedAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (status == null) status = DatingLikeStatus.PENDING;
    }

    @Transient
    public DatingLikeStatus statusOrDefault() {
        return DatingLikeStatus.orDefault(status);
    }

    /** 상대가 좋아요로 답해 만남 약속을 잡을 수 있는 상태인지. */
    @Transient
    public boolean isAccepted() {
        return statusOrDefault() == DatingLikeStatus.ACCEPTED;
    }
}
