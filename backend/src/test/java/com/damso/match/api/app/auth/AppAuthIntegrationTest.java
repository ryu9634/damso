package com.damso.match.api.app.auth;

import com.damso.match.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AppAuthIntegrationTest extends IntegrationTest {

    @Test
    @DisplayName("가입하면 바로 로그인 상태가 되고, 같은 아이디로 다시 로그인할 수 있다")
    void signupThenLogin() {
        AppUser user = signup();

        JsonNode me = ok(get("/api/auth/me", user.token()));
        assertThat(me.get("loginId").asText()).isEqualTo(user.loginId());

        JsonNode login = ok(post("/api/auth/login",
                Map.of("loginId", user.loginId(), "password", "password1234"), null));
        assertThat(login.get("token").asText()).isNotBlank().isNotEqualTo(user.token());
    }

    @Test
    @DisplayName("이미 쓰는 아이디로 가입하면 409")
    void duplicateLoginId() {
        AppUser user = signup();
        int n = uniqueId();

        var res = post("/api/auth/signup", Map.of(
                "loginId", user.loginId(),
                "password", "password1234",
                "nickname", "다른" + n,
                "name", "다른회원",
                "birthDate", "1995-01-01",
                "phone", String.format("011%08d", n),
                "agreedPrivacy", true), null);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("요청 검증에 걸리면 400 과 함께 이유를 알려 준다")
    void validationError() {
        var res = post("/api/auth/signup", Map.of(
                "loginId", "a",            // 4자 미만
                "password", "short",       // 8자 미만
                "nickname", "닉네임",
                "name", "회원",
                "birthDate", "1995-01-01",
                "phone", "0101234",        // 형식 오류
                "agreedPrivacy", true), null);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody().get("message").asText()).isNotBlank();
    }

    @Test
    @DisplayName("비밀번호가 틀리면 400, 토큰 없이 회원 API 를 부르면 401")
    void rejectsBadCredentials() {
        AppUser user = signup();

        var wrong = post("/api/auth/login", Map.of("loginId", user.loginId(), "password", "wrong-password"), null);
        assertThat(wrong.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        assertThat(get("/api/auth/me", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("개인정보(성명·전화번호)는 DB 에 암호문으로 저장되고, API 로는 평문으로 읽힌다")
    void personalDataIsEncryptedAtRest() {
        AppUser user = signup();

        Map<String, Object> row = jdbc.queryForMap(
                "select name, phone, password_hash from app_users where id = ?", user.id());

        assertThat((String) row.get("phone")).startsWith("enc1:").doesNotContain(user.phone());
        assertThat((String) row.get("name")).startsWith("enc1:");
        assertThat((String) row.get("password_hash")).startsWith("$2"); // BCrypt

        JsonNode me = ok(get("/api/auth/me", user.token()));
        assertThat(me.get("phone").asText()).isEqualTo(user.phone());
    }
}
