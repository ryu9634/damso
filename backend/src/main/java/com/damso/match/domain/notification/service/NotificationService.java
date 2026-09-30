package com.damso.match.domain.notification.service;

import com.damso.match.api.app.notification.dto.NotificationDto;
import com.damso.match.domain.dating.entity.DatingProfile;
import com.damso.match.domain.dating.repository.DatingProfileRepository;
import com.damso.match.domain.member.entity.AppUser;
import com.damso.match.domain.member.repository.AppUserRepository;
import com.damso.match.domain.notification.entity.AppNotification;
import com.damso.match.domain.notification.entity.NotificationType;
import com.damso.match.domain.notification.repository.AppNotificationRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 앱 알림.
 *  - 알림은 항상 알림함(app_notifications)에 쌓인다. 앱은 종 아이콘에서 이 목록을 보여주고,
 *    화면이 열려 있는 동안에는 브라우저 알림으로도 띄운다.
 *  - 앱을 닫아둔 사이에도 받아볼 수 있게 웹 푸시({@link WebPushService})로도 밀어준다.
 *    푸시를 켜둔 기기가 없으면 알림함에만 쌓인다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    /** 알림함에 보여줄 최대 개수. */
    private static final int LIST_LIMIT = 100;

    private final AppNotificationRepository repo;
    private final AppUserRepository userRepo;
    private final DatingProfileRepository profileRepo;
    private final WebPushService webPush;

    // ─── 보내기 ─────────────────────────────────────────

    @Transactional
    public void notifyUser(Long userId, NotificationType type,
                           String title, String body, String linkPath) {
        if (userId == null) return;
        AppUser user = userRepo.findById(userId).filter(u -> u.getDeletedAt() == null).orElse(null);
        if (user == null) return;

        AppNotification saved = repo.save(AppNotification.builder()
                .userId(userId)
                .type(type)
                .title(title)
                .body(body)
                .linkPath(linkPath)
                .build());
        sendPush(user, saved);
    }

    /**
     * 소개팅 프로필의 주인에게 알림을 보낸다.
     * 계정 도입 전에 등록돼 아직 계정에 연결되지 않은 프로필이면 아무 일도 하지 않는다.
     */
    @Transactional
    public void notifyProfileOwner(Long profileId, NotificationType type,
                                   String title, String body, String linkPath) {
        DatingProfile p = profileId == null ? null : profileRepo.findById(profileId).orElse(null);
        if (p == null || p.getAppUserId() == null) return;
        notifyUser(p.getAppUserId(), type, title, body, linkPath);
    }

    /**
     * 등록된 기기로 웹 푸시를 민다.
     * 실패해도 알림함에는 이미 남아 있으므로 알림 저장 자체를 되돌리지는 않는다.
     */
    private void sendPush(AppUser user, AppNotification n) {
        try {
            webPush.push(user.getId(), n);
        } catch (Exception e) {
            log.warn("푸시 발송 실패 (회원 {}): {}", user.getId(), e.getMessage());
        }
    }

    // ─── 읽기 ───────────────────────────────────────────

    public List<NotificationDto> list(AppUser user) {
        return repo.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .limit(LIST_LIMIT)
                .map(NotificationDto::from)
                .toList();
    }

    public int unreadCount(AppUser user) {
        return repo.countByUserIdAndReadAtIsNull(user.getId());
    }

    @Transactional
    public void markRead(AppUser user, Long id) {
        AppNotification n = repo.findById(id)
                .filter(x -> x.getUserId().equals(user.getId()))
                .orElseThrow(() -> new IllegalArgumentException("알림을 찾을 수 없어요."));
        if (n.getReadAt() == null) {
            n.setReadAt(LocalDateTime.now());
            repo.save(n);
        }
    }

    @Transactional
    public void markAllRead(AppUser user) {
        LocalDateTime now = LocalDateTime.now();
        for (AppNotification n : repo.findByUserIdOrderByCreatedAtDesc(user.getId())) {
            if (n.getReadAt() != null) continue;
            n.setReadAt(now);
            repo.save(n);
        }
    }

    /** 탈퇴 정리용. 알림함과 등록된 기기를 함께 지운다. */
    @Transactional
    public void deleteAllOf(Long userId) {
        repo.deleteByUserId(userId);
        webPush.unsubscribe(userId, null);
    }
}
