package com.damso.match.api.admin.auth;

import com.damso.match.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 관리자 API 는 인터셉터가 통째로 잠근다. 토큰 발급·검증·폐기를 HTTP 로 확인한다. */
class AdminAuthIntegrationTest extends IntegrationTest {

    @Test
    @DisplayName("토큰 없이 관리자 API 를 부르면 401")
    void rejectsWithoutToken() {
        assertThat(get("/api/admin/events", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(get("/api/admin/events", "not-a-real-token").getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("비밀번호가 틀리면 400, 토큰을 주지 않는다")
    void rejectsWrongPassword() {
        var res = post("/api/admin/auth/login", Map.of("username", "admin", "password", "wrong"), null);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody().has("token")).isFalse();
    }

    @Test
    @DisplayName("로그인 → 토큰으로 관리자 API 통과 → 로그아웃하면 같은 토큰이 막힌다")
    void loginAndLogout() {
        String token = adminToken();

        JsonNode me = ok(get("/api/admin/auth/me", token));
        assertThat(me.get("username").asText()).isEqualTo("admin");
        assertThat(get("/api/admin/events", token).getStatusCode()).isEqualTo(HttpStatus.OK);

        ok(post("/api/admin/auth/logout", null, token));

        assertThat(get("/api/admin/events", token).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
