package com.damso.match.api.admin.auth;

import com.damso.match.domain.admin.entity.AdminUser;
import com.damso.match.domain.admin.service.AdminAuthService;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * 관리자 로그인 API.
 *  - /login 만 비로그인 접근 가능. 나머지 /api/admin/** 은 인터셉터가 토큰을 검사한다.
 */
@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest req) {
        AdminAuthService.LoginResult result = adminAuthService.login(req.username, req.password);
        return new LoginResponse(result.token(), AdminDto.from(result.admin()));
    }

    @PostMapping("/logout")
    public void logout(@RequestHeader(value = "Authorization", required = false) String auth) {
        adminAuthService.logout(auth);
    }

    /** 로그인 상태 확인 (관리자 화면 진입 시 호출). */
    @GetMapping("/me")
    public AdminDto me(@RequestHeader(value = "Authorization", required = false) String auth) {
        return AdminDto.from(adminAuthService.requireAdmin(auth));
    }

    @PutMapping("/password")
    public AdminDto changePassword(@RequestHeader(value = "Authorization", required = false) String auth,
                                   @RequestBody PasswordRequest req) {
        AdminUser admin = adminAuthService.requireAdmin(auth);
        adminAuthService.changePassword(admin, req.currentPassword, req.newPassword);
        return AdminDto.from(admin);
    }

    public static class LoginRequest {
        public String username;
        public String password;
    }

    public static class PasswordRequest {
        public String currentPassword;
        public String newPassword;
    }

    public record LoginResponse(String token, AdminDto admin) {}

    /** 관리자 본인 정보. initialPassword 가 true 면 화면에서 비밀번호 변경을 안내한다. */
    public record AdminDto(Long id, String username, boolean initialPassword, LocalDateTime lastLoginAt) {
        public static AdminDto from(AdminUser a) {
            return new AdminDto(a.getId(), a.getUsername(),
                    Boolean.TRUE.equals(a.getInitialPassword()), a.getLastLoginAt());
        }
    }
}
