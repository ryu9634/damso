package com.damso.match.global.schema;

import com.damso.match.global.config.DataSeeder;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 앱 회원 테이블 스키마 보정. (ddl-auto=update 가 처리하지 못하는 부분만)
 *
 * <p>초기 버전은 로그인 수단을 app_users.provider / provider_id 로 갖고 있었다.
 * 지금은 아이디+비밀번호와 user_social_links 로 분리했는데, ddl-auto=update 는 컬럼을 지우지 않아
 * NOT NULL 인 provider 때문에 회원 INSERT 가 전부 실패한다. 그래서 여기서 정리한다.
 *
 * <p>순서가 중요하다.
 * <ol>
 *   <li>NOT NULL 완화 — 이것만으로도 INSERT 는 통과한다(안전장치)</li>
 *   <li>두 컬럼을 참조하는 유니크 제약 제거 — 안 지우면 컬럼을 못 지운다</li>
 *   <li>컬럼 삭제</li>
 * </ol>
 * 계정 시드(DataSeeder)보다 먼저 실행돼야 한다.
 */
@Slf4j
@Component
@Order(0)
public class AppUserSchemaMigration implements CommandLineRunner {

    /** 더 이상 쓰지 않는 컬럼. */
    private static final List<String> LEGACY_COLS = List.of("provider", "provider_id");

    private final JdbcTemplate jdbc;

    public AppUserSchemaMigration(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(String... args) {
        if (!hasLegacyColumn()) return;

        // 1) 우선 INSERT 가 막히지 않게 NOT NULL 을 푼다.
        for (String col : LEGACY_COLS) {
            SchemaOps.dropNotNull(jdbc, "app_users", col);
        }

        // 2) 두 컬럼을 물고 있는 제약(유니크)을 먼저 없앤다.
        for (String constraint : legacyConstraints()) {
            run("ALTER TABLE app_users DROP CONSTRAINT IF EXISTS " + constraint,
                    "app_users 레거시 제약 " + constraint + " 제거");
        }

        // 3) 컬럼 삭제.
        for (String col : LEGACY_COLS) {
            run("ALTER TABLE app_users DROP COLUMN IF EXISTS " + col,
                    "app_users." + col + " 삭제");
        }
    }

    private boolean hasLegacyColumn() {
        try {
            Integer n = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns "
                            + "WHERE LOWER(table_name) = 'app_users' "
                            + "AND LOWER(column_name) IN ('provider', 'provider_id')",
                    Integer.class);
            return n != null && n > 0;
        } catch (Exception e) {
            // 첫 실행이라 테이블이 아직 없는 경우 등 — 할 일 없음
            log.debug("app_users 레거시 컬럼 확인 건너뜀: {}", e.getMessage());
            return false;
        }
    }

    /** provider / provider_id 를 참조하는 제약 이름 (기본키는 id 라 걸리지 않는다). */
    private List<String> legacyConstraints() {
        try {
            return jdbc.queryForList(
                    "SELECT DISTINCT constraint_name FROM information_schema.key_column_usage "
                            + "WHERE LOWER(table_name) = 'app_users' "
                            + "AND LOWER(column_name) IN ('provider', 'provider_id')",
                    String.class);
        } catch (Exception e) {
            log.debug("app_users 레거시 제약 조회 건너뜀: {}", e.getMessage());
            return List.of();
        }
    }

    private void run(String sql, String what) {
        try {
            jdbc.execute(sql);
            log.info("스키마 보정 — {}", what);
        } catch (Exception e) {
            // 이미 정리됐거나 해당 DB 에 없는 경우 — 넘어간다
            log.debug("스키마 보정 건너뜀({}): {}", what, e.getMessage());
        }
    }
}
