package com.damso.match.api.app.notification;

import com.damso.match.api.app.notification.dto.NotificationDto;
import com.damso.match.domain.member.service.AuthService;
import com.damso.match.domain.notification.service.NotificationService;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 앱 알림함 API. 모두 로그인 필수.
 *  - 좋아요·만남 약속 소식이 여기에 쌓인다.
 */
@RestController
@RequestMapping("/api/app/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notifications;
    private final AuthService authService;

    @GetMapping
    public List<NotificationDto> list(@RequestHeader(value = "Authorization", required = false) String auth) {
        return notifications.list(authService.requireUser(auth));
    }

    /** 종 아이콘 배지용 — 안 읽은 알림 수. */
    @GetMapping("/unread-count")
    public Map<String, Integer> unreadCount(@RequestHeader(value = "Authorization", required = false) String auth) {
        return Map.of("count", notifications.unreadCount(authService.requireUser(auth)));
    }

    @PostMapping("/read-all")
    public void readAll(@RequestHeader(value = "Authorization", required = false) String auth) {
        notifications.markAllRead(authService.requireUser(auth));
    }
}
