package com.damso.match.global.config;

import com.damso.match.global.security.AdminAuthInterceptor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    /** SPA 로 넘기면 안 되는 경로. 여기에 걸리면 index.html 대신 404 를 준다. */
    private static final List<String> BACKEND_PREFIXES = List.of(
            "api/", "uploads/", "h2-console", "v3/api-docs", "swagger-ui");

    @Value("${damso.uploads:./uploads}")
    private String uploadDir;

    /**
     * 허용할 CORS 오리진 패턴 (콤마 구분). 비어 있으면 CORS 매핑 자체를 등록하지 않는다.
     * 운영은 jar 하나가 프론트와 API 를 같은 오리진에서 서빙하므로 비어 있는 것이 정상이다.
     */
    @Value("${damso.cors.allowed-origin-patterns:}")
    private String corsOriginPatterns;

    private final AdminAuthInterceptor adminAuthInterceptor;

    /** 관리자 API 는 로그인 토큰이 있어야 호출할 수 있다. (로그인 요청만 예외) */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminAuthInterceptor)
                .addPathPatterns("/api/admin/**")
                .excludePathPatterns("/api/admin/auth/login");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] patterns = Arrays.stream(corsOriginPatterns.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);

        if (patterns.length == 0) {
            log.info("CORS 매핑 없음 — 동일 오리진에서만 API 를 호출할 수 있다.");
            return;
        }

        log.info("CORS 허용 오리진: {}", String.join(", ", patterns));
        registry.addMapping("/api/**")
                .allowedOriginPatterns(patterns)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 업로드된 소개팅 사진을 /uploads/** 로 정적 제공.
        // 디렉터리로 인식되도록 반드시 끝에 슬래시를 붙인다.
        String location = Paths.get(uploadDir).toAbsolutePath().toUri().toString();
        if (!location.endsWith("/")) location += "/";
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(location);

        // jar 안에 포함된 프론트(Vite 빌드 산출물) 서빙 + SPA 히스토리 라우팅 대응.
        // /admin, /dating 같은 클라이언트 라우트는 서버에 실제 파일이 없으므로 index.html 로 넘겨야
        // 새로고침·직접 접속에서 404 가 나지 않는다. (/uploads/** 가 더 구체적이라 위 핸들러가 우선한다)
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new SpaResourceResolver());
    }

    /** 실제 파일이 있으면 그대로, 없으면 SPA 진입점(index.html)으로 되돌린다. */
    private static class SpaResourceResolver extends PathResourceResolver {
        private final Resource index = new ClassPathResource("static/index.html");

        @Override
        protected Resource getResource(String path, Resource location) throws IOException {
            Resource requested = location.createRelative(path);
            if (requested.exists() && requested.isReadable()) return requested;

            // API·업로드 경로는 없는 것이 곧 404 다. index.html 을 주면 오류를 감추게 된다.
            if (BACKEND_PREFIXES.stream().anyMatch(path::startsWith)) return null;

            // 프론트를 jar 에 넣지 않고 띄운 경우(dev) — 넘길 곳이 없으니 그대로 404.
            return index.exists() ? index : null;
        }
    }
}