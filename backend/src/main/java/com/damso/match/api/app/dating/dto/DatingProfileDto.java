package com.damso.match.api.app.dating.dto;

import com.damso.match.domain.dating.dto.DatingAnswerView;
import com.damso.match.global.common.Gender;

import java.util.List;

/**
 * 이성 프로필 열람용 카드. 이름·연락처는 노출하지 않는다.
 *  - answers: 관리자 커스텀 질문에 대한 답변(질문 순서대로, 빈 값 제외).
 *  - liked: 지금 보고 있는 손님이 이 프로필을 이미 찜했는지.
 *  - verified: 매장에서 직접 얼굴을 확인한 회원인지.
 */
public record DatingProfileDto(
        Long id,
        Gender gender,
        Integer birthYear,
        String photoUrl,
        List<DatingAnswerView> answers,
        boolean liked,
        boolean verified
) {}