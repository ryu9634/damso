package com.damso.match.domain.notification.service;

import com.damso.match.domain.notification.entity.AppNotification;
import com.damso.match.domain.notification.entity.PushSubscription;
import com.damso.match.domain.notification.repository.PushSubscriptionRepository;
import com.damso.match.domain.setting.entity.AppSetting;
import com.damso.match.domain.setting.repository.AppSettingRepository;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.martijndwars.webpush.AbstractPushService;
import nl.martijndwars.webpush.Base64Encoder;
import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.HttpRequest;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.Utils;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 웹 푸시(Web Push · VAPID) 발송.
 *
 * <p>앱을 닫아둔 사이에 온 좋아요·약속을 휴대폰 잠금화면에서 바로 볼 수 있게 한다.
 * 알림함(app_notifications)에는 항상 쌓이고, 이 서비스는 그중 "기기까지 밀어주는" 부분만 담당한다.
 *
 * <p>서버 키(VAPID)는 처음 켤 때 한 번 만들어 설정 테이블에 넣어둔다.
 * 키가 바뀌면 이미 등록된 구독이 전부 무효가 되므로, 한 번 만든 키는 그대로 쓴다.
 *
 * <p>발송은 별도 스레드에서 한다. 좋아요 한 번에 푸시 서버 응답을 기다리게 만들지 않기 위해서다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebPushService {

    private static final String KEY_PUBLIC = "push.vapid.publicKey";
    private static final String KEY_PRIVATE = "push.vapid.privateKey";
    /** RFC 8292 가 요구하는 연락처. 푸시 서비스가 문제 시 연락할 주소다. */
    private static final String SUBJECT = "mailto:damso@damsobar.kro.kr";
    /** 기기가 꺼져 있을 때 푸시 서비스가 보관해 주는 시간(초). */
    private static final int TTL_SECONDS = 12 * 60 * 60;

    private final AppSettingRepository settingRepo;
    private final PushSubscriptionRepository subscriptionRepo;
    private final PlatformTransactionManager txManager;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final ExecutorService sender = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "web-push");
        t.setDaemon(true);
        return t;
    });

    private volatile Vapid vapid;
    private volatile TransactionTemplate tx;

    /** 서버 키 한 쌍. base64url 문자열은 브라우저에 그대로 내려준다. */
    private record Vapid(String publicKey, String privateKey) {}

    // ─── 준비 ───────────────────────────────────────────
    @PostConstruct
    void init() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        tx = new TransactionTemplate(txManager);
        try {
            vapid = loadOrCreateKeys();
        } catch (Exception e) {
            // 키를 못 만들어도 앱은 떠야 한다. 푸시만 꺼진 상태로 동작한다.
            log.warn("웹 푸시 서버 키를 준비하지 못했습니다. 푸시 없이 동작합니다: {}", e.getMessage());
        }
    }

    @PreDestroy
    void shutdown() {
        sender.shutdownNow();
    }

    /** 저장된 서버 키를 읽고, 없으면 새로 만들어 저장한다. (레포지토리 저장이 각자 트랜잭션을 연다) */
    Vapid loadOrCreateKeys() throws Exception {
        String pub = settingRepo.findById(KEY_PUBLIC).map(AppSetting::getSettingValue).orElse(null);
        String priv = settingRepo.findById(KEY_PRIVATE).map(AppSetting::getSettingValue).orElse(null);
        if (pub != null && !pub.isBlank() && priv != null && !priv.isBlank()) {
            return new Vapid(pub, priv);
        }

        ECNamedCurveParameterSpec spec = ECNamedCurveTable.getParameterSpec(Utils.CURVE);
        KeyPairGenerator gen = KeyPairGenerator.getInstance(Utils.ALGORITHM, BouncyCastleProvider.PROVIDER_NAME);
        gen.initialize(spec);
        KeyPair pair = gen.generateKeyPair();

        String newPub = base64url(Utils.encode((ECPublicKey) pair.getPublic()));
        String newPriv = base64url(Utils.encode(
                (org.bouncycastle.jce.interfaces.ECPrivateKey) pair.getPrivate()));
        settingRepo.save(new AppSetting(KEY_PUBLIC, newPub));
        settingRepo.save(new AppSetting(KEY_PRIVATE, newPriv));
        log.info("웹 푸시 서버 키를 새로 만들었습니다.");
        return new Vapid(newPub, newPriv);
    }

    /** 브라우저가 구독할 때 필요한 서버 공개키. 준비가 안 됐으면 null. */
    public String publicKey() {
        Vapid v = vapid;
        return v == null ? null : v.publicKey();
    }

    // ─── 구독 관리 ──────────────────────────────────────
    /** 기기 등록. 같은 기기(endpoint)가 다시 오면 주인만 바꿔 준다. */
    @Transactional
    public void subscribe(Long userId, String endpoint, String p256dh, String auth) {
        if (endpoint == null || endpoint.isBlank() || p256dh == null || auth == null) {
            throw new IllegalArgumentException("구독 정보가 올바르지 않아요.");
        }
        PushSubscription sub = subscriptionRepo.findByEndpoint(endpoint)
                .orElseGet(() -> PushSubscription.builder().endpoint(endpoint).build());
        sub.setAppUserId(userId);
        sub.setP256dh(p256dh);
        sub.setAuth(auth);
        subscriptionRepo.save(sub);
    }

    /** 기기 해제. endpoint 를 주면 그 기기만, 없으면 이 회원의 모든 기기를 뗀다. */
    @Transactional
    public void unsubscribe(Long userId, String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            subscriptionRepo.deleteByAppUserId(userId);
            return;
        }
        subscriptionRepo.findByEndpoint(endpoint)
                .filter(s -> s.getAppUserId().equals(userId))
                .ifPresent(subscriptionRepo::delete);
    }

    /** 이 회원이 푸시를 받도록 등록해 둔 기기 수. */
    @Transactional(readOnly = true)
    public int deviceCount(Long userId) {
        return subscriptionRepo.countByAppUserId(userId);
    }

    // ─── 발송 ───────────────────────────────────────────
    /**
     * 알림 하나를 이 회원의 모든 기기로 보낸다.
     * 호출한 쪽을 붙잡지 않도록 실제 발송은 다른 스레드에서 한다.
     */
    @Transactional(readOnly = true)
    public void push(Long userId, AppNotification n) {
        if (vapid == null) return;
        List<PushSubscription> subs = subscriptionRepo.findByAppUserId(userId);
        if (subs.isEmpty()) return;

        String payload = payload(n);
        for (PushSubscription sub : subs) {
            sender.submit(() -> sendOne(sub, payload));
        }
    }

    /** 알림함에 쌓인 내용을 그대로 기기로 보낸다. (서비스워커가 이 JSON 을 읽어 띄운다) */
    private String payload(AppNotification n) {
        return "{\"title\":" + json(n.getTitle())
                + ",\"body\":" + json(n.getBody())
                + ",\"link\":" + json(n.getLinkPath() == null ? "/notifications" : n.getLinkPath())
                + ",\"tag\":" + json(n.getType() == null ? "damso" : n.getType().name())
                + "}";
    }

    private void sendOne(PushSubscription sub, String payload) {
        try {
            Notification notification = new Notification(
                    sub.getEndpoint(),
                    Utils.loadPublicKey(sub.getP256dh()),
                    Base64Encoder.decode(sub.getAuth()),
                    payload.getBytes(StandardCharsets.UTF_8),
                    TTL_SECONDS);

            HttpRequest req = new RequestBuilder().prepare(notification);
            java.net.http.HttpRequest.Builder builder = java.net.http.HttpRequest.newBuilder()
                    .uri(URI.create(req.getUrl()))
                    .timeout(Duration.ofSeconds(10))
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofByteArray(
                            req.getBody() == null ? new byte[0] : req.getBody()));
            for (Map.Entry<String, String> h : req.getHeaders().entrySet()) {
                builder.header(h.getKey(), h.getValue());
            }

            HttpResponse<String> res = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            int code = res.statusCode();
            if (code == 404 || code == 410) {
                // 브라우저를 지웠거나 알림을 껐다 — 죽은 주소라 지운다.
                dropDeadSubscription(sub.getEndpoint());
            } else if (code >= 400) {
                log.warn("웹 푸시 실패 ({}): {}", code, res.body());
            } else {
                markSent(sub.getId());
            }
        } catch (Exception e) {
            log.warn("웹 푸시 발송 오류: {}", e.getMessage());
        }
    }

    private void dropDeadSubscription(String endpoint) {
        tx.executeWithoutResult(status -> subscriptionRepo.deleteByEndpoint(endpoint));
    }

    private void markSent(Long id) {
        tx.executeWithoutResult(status -> subscriptionRepo.findById(id).ifPresent(s -> {
            s.setLastSentAt(LocalDateTime.now());
            subscriptionRepo.save(s);
        }));
    }

    /**
     * VAPID 서명과 payload 암호화는 라이브러리에 맡기고, 실제 전송만 우리가 한다.
     * prepareRequest 가 protected 라 이렇게 얇게 감싼다.
     */
    private class RequestBuilder extends AbstractPushService<RequestBuilder> {
        HttpRequest prepare(Notification notification) throws Exception {
            Vapid v = vapid;
            setPublicKey(v.publicKey());
            setPrivateKey(v.privateKey());
            setSubject(SUBJECT);
            return prepareRequest(notification, Encoding.AES128GCM);
        }
    }

    // ─── 헬퍼 ───────────────────────────────────────────
    private static String base64url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String json(String s) {
        if (s == null) return "\"\"";
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.append('"').toString();
    }
}
