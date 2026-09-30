package com.damso.match.api.app.auth.dto;

import com.damso.match.domain.member.entity.AppUser;
import com.damso.match.domain.member.entity.AuthProvider;

import java.time.LocalDate;
import java.util.List;

/**
 * 본인에게 돌려주는 회원 정보 (마이페이지).
 *  - linkedProviders: 이 계정에 연결된 간편로그인 목록.
 *  - hasPassword: 아이디/비밀번호로도 로그인할 수 있는지.
 */
public record AppUserDto(
        Long id,
        String loginId,
        boolean hasPassword,
        List<AuthProvider> linkedProviders,
        String nickname,
        String name,
        LocalDate birthDate,
        Integer age,
        String phone,
        boolean pushOptIn,
        boolean profileComplete
) {
    public static AppUserDto from(AppUser u, List<AuthProvider> linked) {
        return new AppUserDto(
                u.getId(),
                u.getLoginId(),
                u.hasPassword(),
                linked == null ? List.of() : linked,
                u.getNickname(),
                u.getName(),
                u.getBirthDate(),
                u.getBirthDate() == null ? null : u.age(),
                u.getPhone(),
                Boolean.TRUE.equals(u.getPushOptIn()),
                u.isProfileComplete()
        );
    }
}
