package com.damso.match.global.schema;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 소개팅 진행 단계 스키마 보정.
 *
 * <p>자리 배치가 3회에서 4회로 늘면서 진행 단계에 ROUND_4 가 생겼다.
 * ddl-auto=update 는 enum 컬럼에 걸어둔 CHECK 제약을 갱신해주지 않아,
 * 그대로 두면 4차로 넘기는 순간 제약 위반으로 실패한다. 그래서 여기서 지운다.
 */
@Slf4j
@Component
@Order(0)
public class EventSchemaMigration implements CommandLineRunner {

    private final JdbcTemplate jdbc;

    public EventSchemaMigration(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(String... args) {
        SchemaOps.dropEnumCheck(jdbc, "events", "status");
    }
}
