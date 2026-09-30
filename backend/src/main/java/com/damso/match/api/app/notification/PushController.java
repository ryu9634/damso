package com.damso.match.api.app.notification;

import com.damso.match.domain.member.entity.AppUser;
import com.damso.match.domain.member.service.AuthService;
import com.damso.match.domain.notification.service.WebPushService;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 웹 푸시 구독 API. 모두 로그인 필수.
 *  - 브라우저가 알림 권한을 받으면 기기별 구독 정보(endpoint + 키 2개)를 만들어 여기로 보낸다.
 *  - 서버는 그 주소로 좋아요·약속 알림을 민다. 알림을 끄면 구독을 지운다.
 */
@RestController
@RequestMapping("/api/app/push")
@RequiredArgsConstructor
public class PushController {

    private final WebPushService webPush;
    private final AuthService authService;

    /** 구독에 필요한 서버 공개키 + 지금 이 계정에 등록된 기기 수. */
    @GetMapping
    public Map<String, Object> info(@RequestHeader(value = "Authorization", required = false) String auth) {
        AppUser user = authService.requireUser(auth);
        Map<String, Object> out = new HashMap<>();
        String key = webPush.publicKey();
        out.put("publicKey", key);
        out.put("supported", key != null);
        out.put("devices", webPush.deviceCount(user.getId()));
        return out;
    }

    @PostMapping("/subscribe")
    public Map<String, Object> subscribe(@RequestHeader(value = "Authorization", required = false) String auth,
                                         @RequestBody SubscribeRequest req) {
        AppUser user = authService.requireUser(auth);
        webPush.subscribe(user.getId(), req.endpoint(), req.p256dh(), req.auth());
        return Map.of("devices", webPush.deviceCount(user.getId()));
    }

    @PostMapping("/unsubscribe")
    public Map<String, Object> unsubscribe(@RequestHeader(value = "Authorization", required = false) String auth,
                                           @RequestBody(required = false) SubscribeRequest req) {
        AppUser user = authService.requireUser(auth);
        webPush.unsubscribe(user.getId(), req == null ? null : req.endpoint());
        return Map.of("devices", webPush.deviceCount(user.getId()));
    }

    /** endpoint 는 발송 주소, p256dh/auth 는 그 기기로 보낼 때 쓰는 암호화 키. */
    public record SubscribeRequest(String endpoint, String p256dh, String auth) {}
}
