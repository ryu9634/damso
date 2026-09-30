package com.damso.match.api.admin.event.dto;

import com.damso.match.global.common.Gender;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CustomerUpdateRequest(
        @NotBlank @Size(max = 5, message = "닉네임은 최대 5자까지 입력 가능합니다") String nickname,
        @NotBlank String realName,
        @NotBlank String phone,
        @NotNull Gender gender,
        @NotBlank @Pattern(regexp = "^[EI][SN][TF][JP]$", message = "MBTI 형식이 올바르지 않습니다") String mbti,
        @NotBlank @Pattern(regexp = "^[01]+$", message = "가치관 답변은 0/1 문자열이어야 합니다") String valuesAnswers,
        @NotNull @Min(1) @Max(9) Integer seatNumber
) {}
