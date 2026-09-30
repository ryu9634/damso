package com.damso.match.api.app.auth.dto;

/**
 * 로그인/가입 결과.
 *  - token: 이후 요청의 Authorization: Bearer 값
 *  - needsProfile: 소셜 가입 등으로 성명·생년월일이 비어 있어 보완 입력이 필요한 상태
 */
public record AuthResultDto(
        String token,
        AppUserDto user,
        boolean needsProfile
) {
    public static AuthResultDto of(String token, AppUserDto user) {
        return new AuthResultDto(token, user, !user.profileComplete());
    }
}