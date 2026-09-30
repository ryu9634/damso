package com.damso.match.api.app.dating.dto;

import com.damso.match.domain.dating.dto.DatingAnswerView;
import com.damso.match.domain.dating.entity.DatingLikeStatus;
import com.damso.match.global.common.Gender;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 마이페이지 '받은 좋아요 / 보낸 좋아요' 한 줄.
 *  - 상대의 이름은 서로 좋아요(ACCEPTED)가 된 뒤에만 채운다. 그 전에는 프로필 카드와 같은
 *    수준(사진·출생년도·답변)까지만 보여준다.
 *  - meeting 은 ACCEPTED 일 때만 채워진다.
 *  - expiresAt 은 아직 답을 기다리는(PENDING) 좋아요에만 채워진다. 이 시각이 지나면 자동으로 닫힌다.
 */
public record DatingLikeCardDto(
        Long likeId,
        Long profileId,
        String name,
        Gender gender,
        Integer birthYear,
        String photoUrl,
        List<DatingAnswerView> answers,
        LocalDateTime createdAt,
        DatingLikeStatus status,
        String statusLabel,
        LocalDateTime respondedAt,
        DatingMeetingDto meeting,
        boolean verified,
        LocalDateTime expiresAt
) {}
