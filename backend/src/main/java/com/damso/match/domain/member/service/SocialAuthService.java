package com.damso.match.domain.member.service;

import com.damso.match.domain.member.entity.AuthProvider;
import com.damso.match.global.config.OAuthProperties;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;

/**
 * 네이버/카카오 간편로그인.
 *  - 앱은 인가 코드만 받아서 서버로 넘기고, 토큰 교환·프로필 조회는 서버가 한다. (앱 시크릿 비노출)
 *  - 앱 키가 설정되지 않았으면 {@link IllegalStateException} 을 던져 화면에 안내한다.
 */
@Service
@RequiredArgsConstructor
public class SocialAuthService {

    private final OAuthProperties props;
    private final RestClient rest = RestClient.create();

    /** 제공자에게서 받아온 최소 프로필. (nickname 은 공개 이름 후보) */
    public record SocialProfile(String providerId, String name, String nickname,
                                String phone, LocalDate birthDate) {}

    public OAuthProperties.Provider config(AuthProvider provider) {
        return switch (provider) {
            case KAKAO -> props.getKakao();
            case NAVER -> props.getNaver();
            case LOCAL -> throw new IllegalArgumentException("소셜 로그인 제공자가 아닙니다.");
        };
    }

    public boolean isConfigured(AuthProvider provider) {
        return provider != AuthProvider.LOCAL && config(provider).isConfigured();
    }

    /** 앱이 띄울 동의화면 주소. 미설정이면 null. */
    public String authorizeUrl(AuthProvider provider, String fallbackRedirectUri) {
        if (!isConfigured(provider)) return null;
        OAuthProperties.Provider c = config(provider);
        String redirect = redirectUri(c, fallbackRedirectUri);
        String encodedRedirect = URLEncoder.encode(redirect, StandardCharsets.UTF_8);
        return switch (provider) {
            case KAKAO -> "https://kauth.kakao.com/oauth/authorize"
                    + "?response_type=code&client_id=" + c.getClientId()
                    + "&redirect_uri=" + encodedRedirect;
            case NAVER -> "https://nid.naver.com/oauth2.0/authorize"
                    + "?response_type=code&client_id=" + c.getClientId()
                    + "&redirect_uri=" + encodedRedirect
                    + "&state=damso";
            case LOCAL -> null;
        };
    }

    /** 인가 코드 → 액세스 토큰 → 프로필. */
    public SocialProfile fetchProfile(AuthProvider provider, String code, String state, String redirectUri) {
        OAuthProperties.Provider c = config(provider);
        if (!c.isConfigured()) {
            throw new IllegalStateException(provider.label() + " 로그인이 아직 설정되지 않았어요. 관리자에게 문의해주세요.");
        }
        String redirect = redirectUri(c, redirectUri);
        try {
            String accessToken = provider == AuthProvider.KAKAO
                    ? kakaoToken(c, code, redirect)
                    : naverToken(c, code, state);
            return provider == AuthProvider.KAKAO ? kakaoProfile(accessToken) : naverProfile(accessToken);
        } catch (RestClientException e) {
            throw new IllegalStateException(provider.label() + " 로그인에 실패했어요. 잠시 후 다시 시도해주세요.");
        }
    }

    // ─── 카카오 ─────────────────────────────────────────

    private String kakaoToken(OAuthProperties.Provider c, String code, String redirectUri) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", c.getClientId());
        form.add("redirect_uri", redirectUri);
        form.add("code", code);
        if (c.getClientSecret() != null && !c.getClientSecret().isBlank()) {
            form.add("client_secret", c.getClientSecret());
        }
        Map<?, ?> body = rest.post()
                .uri("https://kauth.kakao.com/oauth/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(Map.class);
        return requireToken(body);
    }

    private SocialProfile kakaoProfile(String accessToken) {
        Map<?, ?> me = rest.get()
                .uri("https://kapi.kakao.com/v2/user/me")
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(Map.class);
        if (me == null || me.get("id") == null) {
            throw new IllegalStateException("카카오 프로필을 가져오지 못했어요.");
        }
        String providerId = String.valueOf(me.get("id"));
        Map<?, ?> account = asMap(me.get("kakao_account"));
        Map<?, ?> profile = asMap(account.get("profile"));

        String nickname = str(profile.get("nickname"));
        String name = str(account.get("name"));
        if (name == null) name = nickname;
        String phone = str(account.get("phone_number")); // 예: +82 10-1234-5678
        // birthyear(YYYY) + birthday(MMDD) 는 각각 별도 동의 항목이라 둘 다 있을 때만 생년월일이 된다.
        LocalDate birth = parseBirth(str(account.get("birthyear")), str(account.get("birthday")));
        return new SocialProfile(providerId, name, nickname, phone, birth);
    }

    // ─── 네이버 ─────────────────────────────────────────

    private String naverToken(OAuthProperties.Provider c, String code, String state) {
        String url = UriComponentsBuilder.fromUriString("https://nid.naver.com/oauth2.0/token")
                .queryParam("grant_type", "authorization_code")
                .queryParam("client_id", c.getClientId())
                .queryParam("client_secret", c.getClientSecret() == null ? "" : c.getClientSecret())
                .queryParam("code", code)
                .queryParam("state", state == null ? "damso" : state)
                .toUriString();
        Map<?, ?> body = rest.get().uri(url).retrieve().body(Map.class);
        return requireToken(body);
    }

    private SocialProfile naverProfile(String accessToken) {
        Map<?, ?> body = rest.get()
                .uri("https://openapi.naver.com/v1/nid/me")
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(Map.class);
        Map<?, ?> res = asMap(body == null ? null : body.get("response"));
        String providerId = str(res.get("id"));
        if (providerId == null) {
            throw new IllegalStateException("네이버 프로필을 가져오지 못했어요.");
        }
        // birthday 는 "MM-DD" 형식이고 birthyear 는 별도 항목이다.
        String birthday = str(res.get("birthday"));
        LocalDate birth = parseBirth(str(res.get("birthyear")),
                birthday == null ? null : birthday.replace("-", ""));
        return new SocialProfile(providerId, str(res.get("name")), str(res.get("nickname")),
                str(res.get("mobile")), birth);
    }

    // ─── 공통 ───────────────────────────────────────────

    private static String requireToken(Map<?, ?> body) {
        String token = body == null ? null : str(body.get("access_token"));
        if (token == null) throw new IllegalStateException("로그인 토큰을 받지 못했어요.");
        return token;
    }

    private static String redirectUri(OAuthProperties.Provider c, String fallback) {
        if (c.getRedirectUri() != null && !c.getRedirectUri().isBlank()) return c.getRedirectUri();
        if (fallback != null && !fallback.isBlank()) return fallback;
        throw new IllegalStateException("소셜 로그인 redirect_uri 가 설정되지 않았어요.");
    }

    private static Map<?, ?> asMap(Object o) {
        return o instanceof Map<?, ?> m ? m : Map.of();
    }

    private static String str(Object o) {
        if (o == null) return null;
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? null : s;
    }

    /** yyyy + MMdd → LocalDate. 둘 중 하나라도 없으면 null (앱에서 보완 입력을 받는다). */
    private static LocalDate parseBirth(String year, String mmdd) {
        if (year == null || mmdd == null || year.length() != 4 || mmdd.length() != 4) return null;
        try {
            return LocalDate.of(
                    Integer.parseInt(year),
                    Integer.parseInt(mmdd.substring(0, 2)),
                    Integer.parseInt(mmdd.substring(2, 4)));
        } catch (NumberFormatException | java.time.DateTimeException e) {
            return null;
        }
    }
}