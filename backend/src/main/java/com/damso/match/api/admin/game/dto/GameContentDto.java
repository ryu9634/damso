package com.damso.match.api.admin.game.dto;

import com.damso.match.domain.game.entity.GameContent;
import com.damso.match.domain.game.entity.GameType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 게임 문항 한 줄 (관리자). */
public record GameContentDto(
        Long id,
        GameType type,
        String typeLabel,
        String optionA,
        String optionB,
        boolean visible,
        int sortOrder
) {
    public static GameContentDto from(GameContent c) {
        return new GameContentDto(
                c.getId(), c.getType(), c.getType().label(),
                c.getOptionA(), c.getOptionB(),
                c.isVisibleItem(), c.getSortOrder() == null ? 0 : c.getSortOrder());
    }

    /** 등록·수정 요청. 밸런스 게임은 optionB 까지 필요하고, 랜덤 뽑기는 optionA(벌칙)만 쓴다. */
    public record SaveRequest(
            @NotNull(message = "게임 종류를 골라주세요") GameType type,
            @NotBlank(message = "내용을 입력해주세요")
            @Size(max = 200, message = "최대 200자입니다") String optionA,
            @Size(max = 200, message = "최대 200자입니다") String optionB,
            Boolean visible,
            Integer sortOrder
    ) {}
}
