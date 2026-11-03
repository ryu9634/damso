package com.damso.match.api.admin.dating.dto;

import com.damso.match.api.app.dating.dto.DatingProfileCreateRequest;
import com.damso.match.domain.dating.entity.DatingStatus;
import com.damso.match.global.common.Gender;

import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 관리자 소개팅 관리 API 요청 바디 모음.
 * (Java 는 파일당 public 타입 1개만 허용하므로 한 클래스 안에 중첩 record 로 모아둔다)
 */
public final class DatingAdminRequests {

    private DatingAdminRequests() {}

    /** 프로필 수정 — 기본 정보 + 커스텀 질문 답변 전체 교체. */
    public record ProfileUpdate(
            @NotBlank @Size(max = 30, message = "이름은 최대 30자입니다") String name,
            @NotBlank @Size(max = 30, message = "연락처를 입력해주세요") String contact,
            @NotNull @Min(value = 1940, message = "출생년도를 확인해주세요") @Max(2012) Integer birthYear,
            @NotNull Gender gender,
            String photoUrl,
            List<DatingProfileCreateRequest.AnswerInput> answers
    ) {}

    /** 상태 변경. */
    public record StatusChange(@NotNull DatingStatus status) {}

    /** 관리자 메모 변경. */
    public record MemoChange(@Size(max = 500, message = "메모는 최대 500자입니다") String memo) {}

    /** 여러 명 일괄 처리 (상태 변경 시 status 사용). */
    public record Bulk(@NotEmpty List<Long> ids, DatingStatus status) {}

    /**
     * 페어 메모 + 데이트 약속 저장.
     * dateDay 는 "yyyy-MM-dd", dateHour 는 19~23 (한 시간 단위). 둘 다 비워두면 '미정'.
     */
    public record PairNoteSave(
            @Size(max = 500, message = "메모는 최대 500자입니다") String memo,
            LocalDate dateDay,
            @Min(value = 19, message = "데이트 시간은 19시부터 선택할 수 있습니다")
            @Max(value = 23, message = "데이트 시간은 23시까지 선택할 수 있습니다")
            Integer dateHour
    ) {}
}