package com.damso.match.api.admin.dating.dto;

import com.damso.match.domain.dating.entity.DatingLikeStatus;
import com.damso.match.global.common.Gender;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 관리자용 '선택(찜)' 한 건 — 누가(from) 누구를(to) 언제 선택했는지.
 *  - mutual: 상대도 이 사람을 선택해 쌍방이 된 상태인지
 *  - mutualAt: 쌍방이 성립한 시각(두 선택 중 나중 시각). 쌍방이 아니면 null
 *  - partnerAt: 상대(to)가 이 사람(from)을 선택한 시각. 쌍방이 아니면 null.
 *    쌍방은 여자→남자 한 줄만 내려주므로, 지워진 남자→여자 줄의 날짜를 여기서 보존한다.
 *  - fromDeleted/toDeleted: 휴지통에 있는 회원인지 (목록에 표시만 하고 숨기지는 않는다)
 *  - memo/dateDay/dateHour: 페어 단위 관리자 메모와 데이트 약속. 쌍방이면 양쪽 줄이 같은 값을 공유한다.
 *  - status: 받은 사람이 좋아요/미안해요 중 무엇으로 답했는지 (아직이면 답변 대기)
 */
public record DatingLikeDto(
        Long id,
        Long fromId,
        String fromName,
        Gender fromGender,
        Integer fromBirthYear,
        boolean fromDeleted,
        Long toId,
        String toName,
        Gender toGender,
        Integer toBirthYear,
        boolean toDeleted,
        LocalDateTime createdAt,
        boolean mutual,
        LocalDateTime mutualAt,
        LocalDateTime partnerAt,
        String memo,
        LocalDate dateDay,
        Integer dateHour,
        DatingLikeStatus status,
        String statusLabel
) {}
