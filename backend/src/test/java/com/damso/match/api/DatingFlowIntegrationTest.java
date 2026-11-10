package com.damso.match.api;

import com.damso.match.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 셀렉팅(상시 소개팅): 프로필 등록 → 관리자 심사 → 좋아요 → 수락까지.
 * 심사 전에는 아무것도 할 수 없다는 규칙과, 이름이 쌍방 수락 뒤에만 공개된다는 규칙을 함께 본다.
 */
class DatingFlowIntegrationTest extends IntegrationTest {

    private String admin;
    private List<Map<String, Object>> answers;

    @BeforeEach
    void setUp() {
        admin = adminToken();
        answers = new ArrayList<>();
        for (JsonNode q : ok(get("/api/dating/questions", null))) {
            String value = switch (q.get("type").asText()) {
                case "SELECT" -> q.get("options").get(0).asText();
                case "NUMBER" -> "170";
                default -> "테스트 답변";
            };
            answers.add(Map.of("questionId", q.get("id").asLong(), "value", value));
        }
    }

    @Test
    @DisplayName("심사 전(PENDING) 프로필은 좋아요를 보낼 수 없다")
    void pendingProfileCannotLike() {
        Profile me = createProfile("MALE");
        Profile other = createProfile("FEMALE");
        approve(other);

        var res = post("/api/dating/profiles/" + me.id() + "/likes/" + other.id(), null, me.token());

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("남의 프로필 번호로는 좋아요를 보낼 수 없다")
    void cannotActAsSomeoneElse() {
        Profile me = createProfile("MALE");
        Profile victim = createProfile("MALE");
        Profile target = createProfile("FEMALE");
        approve(me);
        approve(victim);
        approve(target);

        var res = post("/api/dating/profiles/" + victim.id() + "/likes/" + target.id(), null, me.token());

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("좋아요를 받은 쪽이 수락하면 서로 좋아요가 되고, 그때부터 이름이 보인다")
    void likeThenAccept() {
        Profile man = createProfile("MALE");
        Profile woman = createProfile("FEMALE");
        approve(man);
        approve(woman);

        ok(post("/api/dating/profiles/" + man.id() + "/likes/" + woman.id(), null, man.token()));

        JsonNode received = ok(get("/api/dating/profiles/" + woman.id() + "/likes/received", woman.token()));
        assertThat(received.size()).isEqualTo(1);
        JsonNode like = received.get(0);
        assertThat(like.get("status").asText()).isEqualTo("PENDING");
        assertThat(like.get("name").isNull()).as("수락 전에는 이름을 숨긴다").isTrue();

        JsonNode afterAccept = ok(post(
                "/api/dating/profiles/" + woman.id() + "/likes/" + like.get("likeId").asLong() + "/respond",
                Map.of("accept", true), woman.token()));
        assertThat(afterAccept.get(0).get("status").asText()).isEqualTo("ACCEPTED");
        assertThat(afterAccept.get(0).get("name").asText()).isEqualTo(man.name());

        JsonNode sent = ok(get("/api/dating/profiles/" + man.id() + "/likes/sent", man.token()));
        assertThat(sent.get(0).get("status").asText()).isEqualTo("ACCEPTED");
    }

    // ─── 헬퍼 ───────────────────────────────────────────

    private Profile createProfile(String gender) {
        AppUser user = signup();
        String name = "셀렉팅" + uniqueId();
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("contact", user.phone());
        body.put("birthYear", 1995);
        body.put("gender", gender);
        body.put("answers", answers);
        body.put("agreedPrivacy", true);
        JsonNode res = ok(post("/api/dating/profiles", body, user.token()));
        return new Profile(res.get("id").asLong(), name, user.token());
    }

    private void approve(Profile p) {
        ok(call(HttpMethod.PATCH, "/api/admin/dating/profiles/" + p.id() + "/status",
                Map.of("status", "ACTIVE"), admin));
    }

    private record Profile(long id, String name, String token) {}
}
