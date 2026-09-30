package com.damso.match.api.admin.member.dto;

import com.damso.match.domain.member.entity.AppUser;
import com.damso.match.domain.member.entity.AuthProvider;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 관리자 화면의 앱 회원 한 줄. */
public record AdminAppUserDto(
        Long id,
        String loginId,
        String nickname,
        String name,
        LocalDate birthDate,
        Integer age,
        String phone,
        /** 이 계정이 쓸 수 있는 로그인 수단 (예: 아이디, 카카오) */
        List<String> loginMethods,
        boolean pushOptIn,
        boolean profileComplete,
        LocalDateTime createdAt,
        LocalDateTime lastLoginAt,
        /** 관리자가 비밀번호를 초기화해 준 시각. 손님이 바꾸면 다시 비워진다. */
        LocalDateTime passwordResetAt
) {
    public static AdminAppUserDto from(AppUser u, List<AuthProvider> linked) {
        List<String> methods = new ArrayList<>();
        if (u.hasPassword()) methods.add("아이디");
        if (linked != null) linked.forEach(p -> methods.add(p.label()));

        return new AdminAppUserDto(
                u.getId(),
                u.getLoginId(),
                u.getNickname(),
                u.getName(),
                u.getBirthDate(),
                u.getBirthDate() == null ? null : u.age(),
                u.getPhone(),
                methods,
                Boolean.TRUE.equals(u.getPushOptIn()),
                u.isProfileComplete(),
                u.getCreatedAt(),
                u.getLastLoginAt(),
                u.getPasswordResetAt()
        );
    }
}
