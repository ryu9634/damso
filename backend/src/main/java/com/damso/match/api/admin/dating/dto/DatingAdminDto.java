package com.damso.match.api.admin.dating.dto;

import com.damso.match.domain.dating.dto.DatingAnswerView;
import com.damso.match.domain.dating.entity.DatingStatus;
import com.damso.match.global.common.Gender;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 관리자 전용 소개팅 프로필 (이름·연락처 + 답변 + 찜 현황 + 관리 정보).
 *  - answers: 커스텀 질문 답변(질문 순서대로, 빈 값 제외)
 *  - likedNames: 이 사람이 찜한 상대 이름들
 *  - likedByNames: 이 사람을 찜한 상대 이름들
 *  - mutualNames: 서로 찜한(쌍방) 상대 이름들
 *  - status/adminMemo: 관리자 전용 진행 상태·메모
 *  - deletedAt: 소프트 삭제 시각(NULL 이면 정상)
 *  - verifiedAt: 매장 대면 확인 시각(NULL 이면 미확인)
 *  - hiddenAt: 본인이 노출을 꺼둔 시각(NULL 이면 노출 중)
 */
public record DatingAdminDto(
        Long id,
        String name,
        String contact,
        Integer birthYear,
        Gender gender,
        String photoUrl,
        LocalDateTime createdAt,
        List<DatingAnswerView> answers,
        List<String> likedNames,
        List<String> likedByNames,
        List<String> mutualNames,
        DatingStatus status,
        String adminMemo,
        LocalDateTime deletedAt,
        LocalDateTime verifiedAt,
        LocalDateTime hiddenAt
) {}