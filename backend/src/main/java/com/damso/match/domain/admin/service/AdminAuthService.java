package com.damso.match.domain.admin.service;

import com.damso.match.domain.admin.entity.AdminSession;
import com.damso.match.domain.admin.entity.AdminUser;
import com.damso.match.domain.admin.repository.AdminSessionRepository;
import com.damso.match.domain.admin.repository.AdminUserRepository;
import com.damso.match.domain.member.service.AuthService;
import com.damso.match.global.security.AdminAuthInterceptor;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

/**
 * 관리자 로그인.
 *  - 아이디 + 비밀번호(BCrypt). 세션은 불투명 토큰이며 Authorization: Bearer 로 받는다.
 *  - 관리자 API(/api/admin/**)는 {@code AdminAuthInterceptor} 가 이 서비스로 토큰을 검사한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminAuthService {

    /** 관리자 세션 유효기간(일). 손님보다 짧게 잡는다. */
    private static final int SESSION_DAYS = 14;

    /** 최초 실행 시 만들어지는 기본 계정. */
    public static final String DEFAULT_USERNAME = "admin";
    public static final String DEFAULT_PASSWORD = "admin";

    private final AdminUserRepository userRepo;
    private final AdminSessionRepository sessionRepo;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    // ─── 로그인 / 로그아웃 ──────────────────────────────

    @Transactional
    public LoginResult login(String username, String password) {
        String id = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        AdminUser admin = userRepo.findByUsername(id).orElse(null);
        if (admin == null || !passwordEncoder.matches(password == null ? "" : password, admin.getPasswordHash())) {
            throw new IllegalArgumentException("아이디 또는 비밀번호가 올바르지 않습니다.");
        }
        admin.setLastLoginAt(LocalDateTime.now());
        userRepo.save(admin);

        sessionRepo.deleteByExpiresAtBefore(LocalDateTime.now());
        String token = UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
        sessionRepo.save(AdminSession.builder()
                .token(token)
                .adminId(admin.getId())
                .expiresAt(LocalDateTime.now().plusDays(SESSION_DAYS))
                .build());
        return new LoginResult(token, admin);
    }

    @Transactional
    public void logout(String authorization) {
        String token = extractToken(authorization);
        if (token != null) sessionRepo.deleteById(token);
    }

    /** 관리자 API 접근용 — 유효하지 않으면 401 로 처리되도록 예외를 던진다. */
    public AdminUser requireAdmin(String authorization) {
        AdminUser admin = currentAdmin(authorization);
        if (admin == null) throw new AuthService.UnauthorizedException("관리자 로그인이 필요합니다.");
        return admin;
    }

    public AdminUser currentAdmin(String authorization) {
        String token = extractToken(authorization);
        if (token == null) return null;
        AdminSession session = sessionRepo.findById(token).orElse(null);
        if (session == null || session.isExpired()) return null;
        return userRepo.findById(session.getAdminId()).orElse(null);
    }

    /** 비밀번호 변경 — 기본 비밀번호(admin)를 그대로 두지 않도록 화면에서 안내한다. */
    @Transactional
    public void changePassword(AdminUser admin, String currentPassword, String newPassword) {
        if (!passwordEncoder.matches(currentPassword == null ? "" : currentPassword, admin.getPasswordHash())) {
            throw new IllegalArgumentException("현재 비밀번호가 올바르지 않습니다.");
        }
        if (newPassword == null || newPassword.length() < 4) {
            throw new IllegalArgumentException("새 비밀번호는 4자 이상이어야 합니다.");
        }
        admin.setPasswordHash(passwordEncoder.encode(newPassword));
        admin.setInitialPassword(Boolean.FALSE);
        userRepo.save(admin);
        // 비밀번호를 바꾸면 기존 세션은 모두 끊는다.
        sessionRepo.deleteByAdminId(admin.getId());
    }

    /** 관리자 계정이 하나도 없으면 기본 계정(admin/admin)을 만든다. */
    @Transactional
    public void ensureDefaultAccount() {
        if (userRepo.count() > 0) return;
        userRepo.save(AdminUser.builder()
                .username(DEFAULT_USERNAME)
                .passwordHash(passwordEncoder.encode(DEFAULT_PASSWORD))
                .initialPassword(Boolean.TRUE)
                .build());
    }

    private static String extractToken(String authorization) {
        if (authorization == null || authorization.isBlank()) return null;
        String v = authorization.trim();
        if (v.regionMatches(true, 0, "Bearer ", 0, 7)) v = v.substring(7).trim();
        return v.isEmpty() ? null : v;
    }

    public record LoginResult(String token, AdminUser admin) {}
}
