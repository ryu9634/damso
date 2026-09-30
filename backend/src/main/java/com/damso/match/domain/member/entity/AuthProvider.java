package com.damso.match.domain.member.entity;

/**
 * 앱 회원의 로그인 수단.
 *  - LOCAL: 성명 + 휴대폰번호로 본인 확인 (소셜 키가 없어도 쓸 수 있는 기본 수단)
 *  - KAKAO / NAVER: 간편로그인
 */
public enum AuthProvider {
    LOCAL,
    KAKAO,
    NAVER;

    public String label() {
        return switch (this) {
            case LOCAL -> "휴대폰";
            case KAKAO -> "카카오";
            case NAVER -> "네이버";
        };
    }
}