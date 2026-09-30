package com.damso.match.domain.venue.entity;

import com.damso.match.domain.venue.service.DrinkService;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 앱 홈에 보여주는 술 · 칵테일 메뉴 한 줄.
 *  - 관리자가 직접 등록한다. 이모지만 골라도 되고, 사진을 올리면 사진이 우선 보인다.
 *  - recommended = 오늘의 추천 한 잔. 한 번에 하나만 켜진다.(DrinkService 가 보장)
 *  - visible = false 면 홈에서 감춘다. (지운 게 아니라 잠깐 내려둔 상태)
 */
@Entity
@Table(name = "drink_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DrinkItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 술 이모지 (예: 🍸). 사진이 없을 때 카드에 크게 보인다. */
    @Column(length = 16)
    private String emoji;

    @Column(nullable = false, length = 40)
    private String name;

    @Column(length = 200)
    private String description;

    /** 업로드한 사진 경로 (/uploads/...). 없으면 이모지로 그린다. */
    @Column(length = 255)
    private String imageUrl;

    /** 홈 노출 순서. 작을수록 앞. */
    @Column
    private Integer sortOrder;

    @Column
    private Boolean visible;

    /** 오늘의 추천 한 잔인지. */
    @Column
    private Boolean recommended;

    @Column
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (visible == null) visible = Boolean.TRUE;
        if (recommended == null) recommended = Boolean.FALSE;
        if (sortOrder == null) sortOrder = 0;
    }

    @Transient
    public boolean isVisibleItem() {
        return !Boolean.FALSE.equals(visible);
    }

    @Transient
    public boolean isRecommendedItem() {
        return Boolean.TRUE.equals(recommended);
    }
}
