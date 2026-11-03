package com.damso.match;

import com.damso.match.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 빈 PostgreSQL 에서 앱이 뜨는지 확인한다.
 *
 * <p>컨텍스트가 올라왔다는 것 자체가 검증이다. Flyway 가 V1 을 적용했고, Hibernate 가
 * {@code ddl-auto=validate} 로 모든 엔티티와 테이블을 대조해 통과했다는 뜻이다.
 * 엔티티에 필드를 추가하고 마이그레이션을 빠뜨리면 이 테스트부터 깨진다.
 */
class ApplicationBootIntegrationTest extends IntegrationTest {

    @Test
    @DisplayName("Flyway 마이그레이션이 모두 성공 상태로 적용된다")
    void flywayApplied() {
        Integer failed = jdbc.queryForObject(
                "select count(*) from flyway_schema_history where success = false", Integer.class);
        String latest = jdbc.queryForObject(
                "select max(version) from flyway_schema_history", String.class);

        assertThat(failed).isZero();
        assertThat(latest).isNotBlank();
    }

    @Test
    @DisplayName("헬스체크가 DB 까지 확인하고 UP 을 준다")
    void health() {
        JsonNode body = ok(get("/api/health", null));

        assertThat(body.get("status").asText()).isEqualTo("UP");
        assertThat(body.get("db").asText()).isEqualTo("UP");
    }

    @Test
    @DisplayName("첫 기동 시 기본 관리자 계정과 기본 데이터가 시드된다")
    void seeded() {
        assertThat(jdbc.queryForObject(
                "select count(*) from admin_users where username = 'admin'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from value_questions", Integer.class)).isPositive();
        assertThat(jdbc.queryForObject("select count(*) from dating_questions", Integer.class)).isPositive();
    }
}
