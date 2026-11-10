package com.damso.match.support;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 통합 테스트 공통 기반.
 *
 * <p>실제 PostgreSQL 컨테이너를 띄우고 앱 전체를 임의 포트로 기동한 뒤, HTTP 로 API 를 호출한다.
 * 운영과 같은 경로(Flyway 마이그레이션 → Hibernate validate → 컨트롤러 → 서비스 → DB)를 그대로 지난다.
 *
 * <p>컨테이너는 static 필드 하나로 모든 테스트 클래스가 공유한다(싱글턴 컨테이너 패턴).
 * 스프링 컨텍스트도 설정이 같으면 캐시되므로, 기동 비용은 테스트 실행 전체에서 한 번만 든다.
 * 대신 테스트끼리 DB 를 공유하므로, 각 테스트는 {@link #uniqueId} 로 겹치지 않는 데이터를 만든다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class IntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    protected TestRestTemplate http;

    @Autowired
    protected JdbcTemplate jdbc;

    @BeforeEach
    void useJdkHttpClientForPatch() {
        // 기본 HttpURLConnection 은 PATCH 를 보내지 못한다.
        http.getRestTemplate().setRequestFactory(
                new org.springframework.http.client.JdkClientHttpRequestFactory());
    }

    // ─── HTTP 헬퍼 ───────────────────────────────────────

    protected ResponseEntity<JsonNode> call(HttpMethod method, String path, Object body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) headers.setBearerAuth(token);
        return http.exchange(path, method, new HttpEntity<>(body, headers), JsonNode.class);
    }

    protected ResponseEntity<JsonNode> get(String path, String token) {
        return call(HttpMethod.GET, path, null, token);
    }

    protected ResponseEntity<JsonNode> post(String path, Object body, String token) {
        return call(HttpMethod.POST, path, body, token);
    }

    /** 2xx 를 기대하는 호출. 실패하면 응답 본문을 메시지에 담아 바로 알 수 있게 한다. */
    protected JsonNode ok(ResponseEntity<JsonNode> res) {
        assertThat(res.getStatusCode().is2xxSuccessful())
                .as("기대: 2xx, 실제: %s %s", res.getStatusCode(), res.getBody())
                .isTrue();
        return res.getBody();
    }

    // ─── 테스트 데이터 ───────────────────────────────────

    /** 테스트끼리 겹치지 않는 짧은 번호. 아이디·닉네임·전화번호에 붙인다. */
    protected static int uniqueId() {
        return SEQ.incrementAndGet();
    }

    /** 시드된 기본 관리자(admin/admin)로 로그인한 토큰. */
    protected String adminToken() {
        JsonNode res = ok(post("/api/admin/auth/login",
                Map.of("username", "admin", "password", "admin"), null));
        return res.get("token").asText();
    }

    /** 앱 회원 가입 후 토큰을 돌려준다. */
    protected AppUser signup() {
        int n = uniqueId();
        String loginId = "user" + n;
        Map<String, Object> body = new HashMap<>();
        body.put("loginId", loginId);
        body.put("password", "password1234");
        body.put("nickname", "닉" + n);
        body.put("name", "회원" + n);
        body.put("birthDate", LocalDate.of(1995, 1, 1).toString());
        body.put("phone", String.format("010%08d", n));
        body.put("agreedPrivacy", true);
        JsonNode res = ok(post("/api/auth/signup", body, null));
        return new AppUser(res.get("user").get("id").asLong(), loginId, body.get("phone").toString(),
                res.get("token").asText());
    }

    protected record AppUser(long id, String loginId, String phone, String token) {}
}
