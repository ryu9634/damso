package com.damso.match.api.admin.dating.dto;

import com.damso.match.domain.dating.entity.DatingMatchStage;

import java.time.LocalDateTime;

/**
 * 관리자 매칭 보드 카드 한 장.
 *  - mutualNow: 지금도 쌍방 찜 상태인지 (찜을 취소하면 false 가 되지만 카드는 유지된다)
 */
public record DatingMatchDto(
        Long id,
        Long aId, String aName, String aContact, Integer aBirthYear,
        Long bId, String bName, String bContact, Integer bBirthYear,
        DatingMatchStage stage,
        String memo,
        boolean fromMutual,
        boolean mutualNow,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}