package com.damso.match.api.admin.member;

import com.damso.match.api.admin.member.dto.AdminAppUserDto;
import com.damso.match.domain.member.dto.AuthRequests;
import com.damso.match.domain.member.entity.AppUser;
import com.damso.match.domain.member.service.AuthService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 관리자 — 앱(손님 모바일) 회원 관리.
 *
 * <p>URL 이 {@code /api/admin/app/users} 인 것은 손님 앱을 관리한다는 뜻이고,
 * 다루는 대상은 회원이다. 예전에는 방명록 관리와 한 클래스에 있었는데
 * 둘은 서로 다른 도메인이라 나눴다. URL 은 화면이 쓰고 있으므로 그대로 둔다.
 */
@RestController
@RequestMapping("/api/admin/app/users")
@RequiredArgsConstructor
public class AdminAppUserController {

    private final AuthService authService;

    @GetMapping
    public List<AdminAppUserDto> users() {
        return authService.listAll().stream()
                .map(u -> AdminAppUserDto.from(u, authService.linkedProviders(u)))
                .toList();
    }

    /** 회원 정보 수정 — 닉네임·성명·생년월일·휴대폰번호. 규칙은 손님 마이페이지와 같다. */
    @PutMapping("/{id}")
    public AdminAppUserDto updateUser(@PathVariable Long id,
                                      @Valid @RequestBody AuthRequests.ProfileUpdate req) {
        AppUser user = authService.updateProfileByAdmin(id, req);
        return AdminAppUserDto.from(user, authService.linkedProviders(user));
    }

    /**
     * 비밀번호 초기화 — 임시 비밀번호를 발급한다.
     * 응답에 담긴 값이 평문을 볼 수 있는 유일한 기회다. (서버에는 해시만 남는다)
     */
    @PostMapping("/{id}/password/reset")
    public AuthService.TempPassword resetPassword(@PathVariable Long id) {
        return authService.resetPasswordByAdmin(id);
    }

    /** 탈퇴 처리 (소프트 삭제). */
    @DeleteMapping("/{id}")
    public void removeUser(@PathVariable Long id) {
        authService.removeByAdmin(id);
    }
}
