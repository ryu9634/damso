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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 현장 소개팅 이벤트를 관리자와 손님 양쪽에서 HTTP 로 진행해 본다.
 * 등록 → 1차·2차 자리 배치 → 호감도 지목 → 메시지까지, 실제 화면이 부르는 순서 그대로다.
 */
class EventFlowIntegrationTest extends IntegrationTest {

    private String admin;
    private long eventId;
    private String valuesAnswers;

    @BeforeEach
    void startNewEvent() {
        admin = adminToken();
        JsonNode event = ok(post("/api/admin/events/start-new", Map.of("name", "테스트 이벤트"), admin));
        eventId = event.get("id").asLong();

        // 가치관 답변은 문항 수만큼의 0/1 문자열이다.
        int questions = ok(get("/api/guest/questions", null)).size();
        valuesAnswers = "01".repeat(questions).substring(0, questions);
    }

    @Test
    @DisplayName("손님 등록은 현재 이벤트에 붙고, 같은 계정으로 다시 등록하면 기존 등록을 돌려준다")
    void registerIsIdempotent() {
        AppUser user = signup();

        JsonNode first = register(user, "MALE", 1);
        JsonNode again = register(user, "MALE", 1);

        assertThat(first.get("eventId").asLong()).isEqualTo(eventId);
        assertThat(again.get("id").asLong()).isEqualTo(first.get("id").asLong());
    }

    @Test
    @DisplayName("1차는 등록한 자리 번호대로, 2차는 매칭 점수로 남녀가 한 자리씩 앉는다")
    void seatAssignment() {
        List<Long> men = new ArrayList<>();
        List<Long> women = new ArrayList<>();
        for (int seat = 1; seat <= 3; seat++) {
            men.add(register(signup(), "MALE", seat).get("id").asLong());
            women.add(register(signup(), "FEMALE", seat).get("id").asLong());
        }
        changeStatus("ROUND_1");

        JsonNode round1 = ok(post("/api/admin/seats/round1/auto?eventId=" + eventId, null, admin));
        assertThat(round1.size()).isEqualTo(6);
        assertEachSeatHasOneManAndOneWoman(round1);

        changeStatus("ROUND_2");
        JsonNode round2 = ok(post("/api/admin/seats/round2/auto?eventId=" + eventId, null, admin));
        assertThat(round2.size()).isEqualTo(6);
        assertEachSeatHasOneManAndOneWoman(round2);

        // 손님 화면도 같은 배치를 본다.
        JsonNode guestView = ok(get("/api/guest/seats?customerId=" + men.get(0) + "&round=2", null));
        assertThat(guestView.size()).isPositive();
    }

    @Test
    @DisplayName("호감도는 1·2·3순위가 겹치면 거절하고, 메시지는 같은 상대에게 두 번 보낼 수 없다")
    void preferenceAndMessage() {
        long man = register(signup(), "MALE", 1).get("id").asLong();
        long woman1 = register(signup(), "FEMALE", 1).get("id").asLong();
        long woman2 = register(signup(), "FEMALE", 2).get("id").asLong();

        changeStatus("PREFERENCE");
        Map<String, Object> duplicated = new HashMap<>();
        duplicated.put("customerId", man);
        duplicated.put("firstChoiceId", woman1);
        duplicated.put("secondChoiceId", woman1);
        assertThat(post("/api/guest/preference", duplicated, null).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        ok(post("/api/guest/preference",
                Map.of("customerId", man, "firstChoiceId", woman1, "secondChoiceId", woman2), null));
        JsonNode mine = ok(get("/api/guest/preference?customerId=" + man, null));
        assertThat(mine.get("firstChoiceId").asLong()).isEqualTo(woman1);

        changeStatus("MESSAGE");
        Map<String, Object> message = Map.of(
                "customerId", man, "receiverId", woman1,
                "content", "반가웠어요", "senderContact", "010-0000-0000");
        ok(post("/api/guest/message", message, null));
        assertThat(post("/api/guest/message", message, null).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        JsonNode received = ok(get("/api/guest/me/messages-received?customerId=" + woman1, null));
        assertThat(received.size()).isEqualTo(1);
        assertThat(received.get(0).get("content").asText()).isEqualTo("반가웠어요");
    }

    // ─── 헬퍼 ───────────────────────────────────────────

    private JsonNode register(AppUser user, String gender, int seat) {
        int n = uniqueId();
        Map<String, Object> body = new HashMap<>();
        body.put("eventId", eventId);
        body.put("nickname", "손" + n);
        body.put("realName", "손님" + n);
        body.put("phone", user.phone());
        body.put("gender", gender);
        body.put("mbti", "INFP");
        body.put("valuesAnswers", valuesAnswers);
        body.put("seatNumber", seat);
        return ok(post("/api/guest/customers", body, user.token()));
    }

    private void changeStatus(String status) {
        ok(call(HttpMethod.PATCH, "/api/admin/events/" + eventId + "/status", Map.of("status", status), admin));
    }

    private static void assertEachSeatHasOneManAndOneWoman(JsonNode seats) {
        Map<Integer, Set<String>> genders = new HashMap<>();
        Set<Long> customers = new HashSet<>();
        for (JsonNode s : seats) {
            genders.computeIfAbsent(s.get("seatNumber").asInt(), k -> new HashSet<>())
                    .add(s.get("gender").asText());
            customers.add(s.get("customerId").asLong());
        }
        assertThat(customers).hasSize(seats.size());   // 한 사람이 두 자리에 앉지 않는다
        assertThat(genders.values()).allSatisfy(g -> assertThat(g).containsExactlyInAnyOrder("MALE", "FEMALE"));
    }
}
