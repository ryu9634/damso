package com.damso.match.global.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 네이버/카카오 간편로그인 앱 키.
 *  - application.yml 또는 환경변수로 넣는다. (키를 소스에 커밋하지 않기 위함)
 *      damso.oauth.kakao.client-id / client-secret / redirect-uri
 *      damso.oauth.naver.client-id / client-secret / redirect-uri
 *  - 값이 비어 있으면 해당 버튼은 앱 화면에 노출되지 않는다.
 */
@Component
@ConfigurationProperties(prefix = "damso.oauth")
@Getter
@Setter
public class OAuthProperties {

    private Provider kakao = new Provider();
    private Provider naver = new Provider();

    @Getter
    @Setter
    public static class Provider {
        private String clientId;
        private String clientSecret;
        /** 미설정 시 요청 도메인 기준으로 자동 계산한다. */
        private String redirectUri;

        public boolean isConfigured() {
            return clientId != null && !clientId.isBlank();
        }
    }
}