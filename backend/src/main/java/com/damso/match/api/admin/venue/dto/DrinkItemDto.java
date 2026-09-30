package com.damso.match.api.admin.venue.dto;

import com.damso.match.domain.venue.entity.DrinkItem;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 술 메뉴 한 줄. (관리자 목록 · 앱 홈 공용) */
public record DrinkItemDto(
        Long id,
        String emoji,
        String name,
        String description,
        String imageUrl,
        int sortOrder,
        boolean visible,
        boolean recommended
) {
    public static DrinkItemDto from(DrinkItem d) {
        return new DrinkItemDto(
                d.getId(),
                d.getEmoji(),
                d.getName(),
                d.getDescription(),
                d.getImageUrl(),
                d.getSortOrder() == null ? 0 : d.getSortOrder(),
                d.isVisibleItem(),
                d.isRecommendedItem());
    }

    /** 등록 · 수정 요청. */
    public record SaveRequest(
            @Size(max = 16, message = "이모지는 최대 16자입니다") String emoji,
            @NotBlank(message = "이름을 입력해주세요")
            @Size(max = 40, message = "이름은 최대 40자입니다") String name,
            @Size(max = 200, message = "설명은 최대 200자입니다") String description,
            @Size(max = 255) String imageUrl,
            Integer sortOrder,
            Boolean visible,
            Boolean recommended
    ) {}
}
