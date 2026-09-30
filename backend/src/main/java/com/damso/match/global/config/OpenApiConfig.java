package com.damso.match.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI(/swagger-ui.html) 설정.
 *
 * <p>관리자·앱·손님 API 는 쓰는 토큰이 달라 그룹을 나눈다. 로그인 응답의 토큰을
 * 오른쪽 위 Authorize 에 넣으면 이후 요청에 Authorization: Bearer 가 붙는다.
 *
 * <p>켜고 끄는 것은 springdoc.api-docs.enabled 가 한다 (dev 만 켜짐, 운영은 DAMSO_SWAGGER=true).
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearer";

    @Bean
    public OpenAPI damsoOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("담소 매칭 API")
                        .description("담소혼술바 매칭 프로그램 백엔드 API")
                        .version("v1"))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .description("로그인 응답으로 받은 토큰 (Bearer 없이 값만)")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }

    @Bean
    public GroupedOpenApi adminApi() {
        return group("1-admin", "/api/admin/**");
    }

    @Bean
    public GroupedOpenApi appApi() {
        return group("2-app", "/api/app/**");
    }

    @Bean
    public GroupedOpenApi guestApi() {
        return GroupedOpenApi.builder()
                .group("3-guest")
                .pathsToMatch("/api/**")
                .pathsToExclude("/api/admin/**", "/api/app/**")
                .addOperationCustomizer(hideAuthorizationHeader())
                .build();
    }

    private GroupedOpenApi group(String name, String path) {
        return GroupedOpenApi.builder()
                .group(name)
                .pathsToMatch(path)
                .addOperationCustomizer(hideAuthorizationHeader())
                .build();
    }

    /**
     * 컨트롤러가 @RequestHeader("Authorization") 으로 토큰을 직접 받으므로 엔드포인트마다 입력칸이 생긴다.
     * Swagger UI 는 이 칸에 넣은 값을 보내지 않으므로(OpenAPI 규격상 무시) 헷갈리지 않게 숨기고
     * Authorize 버튼 하나로 대신한다.
     */
    private OperationCustomizer hideAuthorizationHeader() {
        return (operation, handlerMethod) -> {
            if (operation.getParameters() != null) {
                operation.getParameters().removeIf(p ->
                        "header".equals(p.getIn()) && "Authorization".equalsIgnoreCase(p.getName()));
            }
            return operation;
        };
    }
}
