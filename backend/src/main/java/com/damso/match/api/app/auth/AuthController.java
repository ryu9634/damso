package com.damso.match.api.app.auth;

import com.damso.match.api.app.auth.dto.AppUserDto;
import com.damso.match.api.app.auth.dto.AuthProviderDto;
import com.damso.match.api.app.auth.dto.AuthResultDto;
import com.damso.match.domain.member.dto.AuthRequests;
import com.damso.match.domain.member.entity.AppUser;
import com.damso.match.domain.member.entity.AuthProvider;
import com.damso.match.domain.member.service.AuthService;
import com.damso.match.domain.member.service.SocialAuthService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 앱 회원 인증 API.
 *  - 기본 로그인: 아이디 + 비밀번호
 *  - 간편로그인: 네이버·카카오 (계정에 "연결"되는 방식이라 아이디 계정과 충돌하지 않는다)
 *  - 로그인 후 발급되는 토큰을 Authorization: Bearer 로 보낸다.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final SocialAuthService socialAuthService;

    // ─── 가입 / 로그인 ──────────────────────────────────

    @PostMapping("/signup")
    public AuthResultDto signup(@Valid @RequestBody AuthRequests.Signup req) {
        return result(authService.signup(req));
    }

    @PostMapping("/login")
    public AuthResultDto login(@Valid @RequestBody AuthRequests.Login req) {
        return result(authService.login(req));
    }

    /** 아이디 중복 확인 (가입 화면에서 실시간 안내용). */
    @GetMapping("/check-id")
    public Map<String, Boolean> checkId(@RequestParam("loginId") String loginId) {
        return Map.of("available", authService.isLoginIdAvailable(loginId));
    }

    /** 닉네임 중복 확인. */
    @GetMapping("/check-nickname")
    public Map<String, Boolean> checkNickname(@RequestParam("nickname") String nickname) {
        return Map.of("available", authService.isNicknameAvailable(nickname));
    }

    @PostMapping("/logout")
    public void logout(@RequestHeader(value = "Authorization", required = false) String auth) {
        authService.logout(auth);
    }

    // ─── 간편로그인 ────────────────────────────────────

    /** 앱 화면에 어떤 간편로그인 버튼을 띄울지. (앱 키가 없는 제공자는 configured=false) */
    @GetMapping("/providers")
    public List<AuthProviderDto> providers(HttpServletRequest request) {
        return List.of(AuthProvider.KAKAO, AuthProvider.NAVER).stream()
                .map(p -> new AuthProviderDto(
                        p,
                        p.label(),
                        socialAuthService.isConfigured(p),
                        socialAuthService.authorizeUrl(p, defaultRedirectUri(request, p))))
                .toList();
    }

    /**
     * 제공자 동의화면에서 돌아온 인가 코드 처리.
     *  - 비로그인: 연결된 계정이 있으면 로그인, 없으면 새 계정 생성
     *  - 로그인 상태 + link=true: 현재 계정에 연결 (계정 합치기)
     */
    @PostMapping("/oauth/{provider}")
    public AuthResultDto oauth(@PathVariable String provider,
                               @RequestHeader(value = "Authorization", required = false) String auth,
                               @Valid @RequestBody AuthRequests.SocialLogin req,
                               HttpServletRequest request) {
        AuthProvider p = parseProvider(provider);
        String redirect = req.redirectUri() == null || req.redirectUri().isBlank()
                ? defaultRedirectUri(request, p)
                : req.redirectUri();
        AppUser current = Boolean.TRUE.equals(req.link()) ? authService.currentUser(auth) : null;
        SocialAuthService.SocialProfile profile =
                socialAuthService.fetchProfile(p, req.code(), req.state(), redirect);
        return result(authService.socialLogin(p, profile, current));
    }

    /** 간편로그인 연결 해제. */
    @DeleteMapping("/me/social/{provider}")
    public AppUserDto unlink(@RequestHeader(value = "Authorization", required = false) String auth,
                             @PathVariable String provider) {
        AppUser user = authService.requireUser(auth);
        authService.unlinkSocial(user, parseProvider(provider));
        return dto(user);
    }

    // ─── 내 정보 ────────────────────────────────────────

    @GetMapping("/me")
    public AppUserDto me(@RequestHeader(value = "Authorization", required = false) String auth) {
        return dto(authService.requireUser(auth));
    }

    @PutMapping("/me")
    public AppUserDto updateMe(@RequestHeader(value = "Authorization", required = false) String auth,
                               @Valid @RequestBody AuthRequests.ProfileUpdate req) {
        AppUser user = authService.requireUser(auth);
        return dto(authService.updateProfile(user, req));
    }

    /** 비밀번호 변경 (간편로그인 전용 계정은 아이디도 함께 만든다). */
    @PutMapping("/me/password")
    public AppUserDto changePassword(@RequestHeader(value = "Authorization", required = false) String auth,
                                     @Valid @RequestBody AuthRequests.PasswordChange req) {
        AppUser user = authService.requireUser(auth);
        return dto(authService.changePassword(user, req));
    }

    /** 휴대폰 푸시 알람 허용 여부. */
    @PutMapping("/me/push")
    public AppUserDto push(@RequestHeader(value = "Authorization", required = false) String auth,
                           @Valid @RequestBody AuthRequests.PushOptIn req) {
        AppUser user = authService.requireUser(auth);
        return dto(authService.setPushOptIn(user, req));
    }

    @DeleteMapping("/me")
    public void withdraw(@RequestHeader(value = "Authorization", required = false) String auth) {
        authService.withdraw(authService.requireUser(auth));
    }

    // ─── 공통 ───────────────────────────────────────────

    private AppUserDto dto(AppUser user) {
        return AppUserDto.from(user, authService.linkedProviders(user));
    }

    private AuthResultDto result(AuthService.AuthResult r) {
        return AuthResultDto.of(r.token(), dto(r.user()));
    }

    private static AuthProvider parseProvider(String raw) {
        try {
            AuthProvider p = AuthProvider.valueOf(raw.toUpperCase(Locale.ROOT));
            if (p == AuthProvider.LOCAL) throw new IllegalArgumentException();
            return p;
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("지원하지 않는 로그인 방식입니다.");
        }
    }

    /**
     * redirect_uri 기본값 — 앱이 열려 있는 도메인의 /oauth/{provider} 로 되돌아오게 한다.
     * (설정에 redirect-uri 가 있으면 그 값이 우선한다)
     */
    private static String defaultRedirectUri(HttpServletRequest request, AuthProvider provider) {
        String origin = request.getHeader("Origin");
        if (origin == null || origin.isBlank()) {
            String scheme = request.getScheme();
            int port = request.getServerPort();
            boolean defaultPort = ("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443);
            origin = scheme + "://" + request.getServerName() + (defaultPort ? "" : ":" + port);
        }
        return origin + "/oauth/" + provider.name().toLowerCase(Locale.ROOT);
    }
}
