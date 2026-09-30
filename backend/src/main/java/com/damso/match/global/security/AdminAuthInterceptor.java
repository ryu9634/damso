package com.damso.match.global.security;

import com.damso.match.domain.admin.service.AdminAuthService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * 관리자 API 보호.
 *  - /api/admin/** 은 관리자 로그인 토큰이 있어야 통과한다.
 *  - 로그인 자체(/api/admin/auth/login)와 CORS 프리플라이트(OPTIONS)는 예외.
 * 화면만 막으면 API 를 직접 호출할 수 있어 서버에서도 함께 막는다.
 */
@Component
@RequiredArgsConstructor
public class AdminAuthInterceptor implements HandlerInterceptor {

    private final AdminAuthService adminAuthService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) return true;

        if (adminAuthService.currentAdmin(request.getHeader("Authorization")) != null) return true;

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        try {
            response.getWriter().write("{\"status\":401,\"message\":\"관리자 로그인이 필요합니다.\"}");
        } catch (Exception ignored) {
            // 응답 본문을 못 써도 401 상태 코드만으로 클라이언트가 처리한다.
        }
        return false;
    }
}
