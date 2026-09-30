package com.damso.match.api.app.auth.dto;

import com.damso.match.domain.member.entity.AuthProvider;

/**
 * 간편로그인 버튼 노출용.
 *  - configured=false 면 관리자가 아직 앱 키를 넣지 않은 상태라 화면에서 버튼을 숨긴다.
 */
public record AuthProviderDto(
        AuthProvider provider,
        String label,
        boolean configured,
        String authorizeUrl
) {}