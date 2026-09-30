package com.damso.match.global.schema;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

/**
 * 스키마 보정용 DDL 헬퍼.
 *
 * <p>도메인별 마이그레이션(dating·event·account·shared)이 함께 쓰므로 공개한다.
 *
 * <p>NOT NULL 해제 구문은 DB 마다 다르다. H2 는 {@code ALTER COLUMN x SET NULL},
 * PostgreSQL 은 {@code ALTER COLUMN x DROP NOT NULL} 이다. 개발은 H2, 운영은 PostgreSQL 이므로
 * 둘 다 시도하고, 양쪽 모두 실패했을 때만 진짜 실패로 본다.
 */
@Slf4j
public final class SchemaOps {

    private SchemaOps() {}

    /**
     * 컬럼의 NOT NULL 제약을 푼다.
     *
     * @return 실제로 적용됐으면 true. 대상 테이블/컬럼이 없거나 이미 NULL 허용이면 false.
     */
    public static boolean dropNotNull(JdbcTemplate jdbc, String table, String column) {
        String[] variants = {
                "ALTER TABLE " + table + " ALTER COLUMN " + column + " DROP NOT NULL",  // PostgreSQL
                "ALTER TABLE " + table + " ALTER COLUMN " + column + " SET NULL",       // H2
        };

        Exception last = null;
        for (String sql : variants) {
            try {
                jdbc.execute(sql);
                log.info("스키마 보정 — {}.{} NOT NULL 해제", table, column);
                return true;
            } catch (Exception e) {
                last = e;
            }
        }
        // 대상이 없거나(정상: 신규 DB) 이미 해제된 경우가 대부분이라 debug 로만 남긴다.
        log.debug("{}.{} NOT NULL 해제 건너뜀: {}", table, column,
                last == null ? "-" : last.getMessage());
        return false;
    }

    /**
     * varchar 컬럼 폭을 넓힌다. (줄이지는 않는다 — 이미 더 넓으면 DB 가 그대로 둔다)
     *
     * <p>ddl-auto=update 는 기존 컬럼의 길이를 바꾸지 않는다. 암호화처럼 값이 길어지는 변경은
     * 여기서 직접 넓혀야 한다. PostgreSQL 은 {@code TYPE}, H2 는 {@code SET DATA TYPE} 이다.
     */
    public static boolean widenVarchar(JdbcTemplate jdbc, String table, String column, int length) {
        String[] variants = {
                "ALTER TABLE " + table + " ALTER COLUMN " + column + " TYPE varchar(" + length + ")",          // PostgreSQL
                "ALTER TABLE " + table + " ALTER COLUMN " + column + " SET DATA TYPE varchar(" + length + ")", // H2
        };
        Exception last = null;
        for (String sql : variants) {
            try {
                jdbc.execute(sql);
                log.info("스키마 보정 — {}.{} varchar({})", table, column, length);
                return true;
            } catch (Exception e) {
                last = e;
            }
        }
        log.debug("{}.{} 폭 조정 건너뜀: {}", table, column, last == null ? "-" : last.getMessage());
        return false;
    }

    /**
     * enum 컬럼에 걸린 CHECK 제약을 지운다.
     *
     * <p>Hibernate 는 {@code @Enumerated(STRING)} 컬럼에 "값이 이 목록 안에 있어야 한다"는 CHECK 를 건다.
     * 그런데 ddl-auto=update 는 나중에 enum 값이 늘어도 그 제약을 갱신해주지 않아, 새 값을 넣는 순간
     * 제약 위반으로 터진다. 새 테이블은 columnDefinition 으로 제약 자체를 안 만들고,
     * 이미 만들어진 테이블은 여기서 지운다.
     */
    public static void dropEnumCheck(JdbcTemplate jdbc, String table, String column) {
        List<String> names;
        try {
            names = jdbc.queryForList(
                    "SELECT tc.constraint_name "
                            + "FROM information_schema.table_constraints tc "
                            + "JOIN information_schema.check_constraints cc "
                            + "  ON cc.constraint_name = tc.constraint_name "
                            + " AND cc.constraint_schema = tc.constraint_schema "
                            + "WHERE tc.constraint_type = 'CHECK' "
                            + "  AND UPPER(tc.table_name) = ? "
                            + "  AND UPPER(cc.check_clause) LIKE ?",
                    String.class, table.toUpperCase(), "%" + column.toUpperCase() + "%");
        } catch (Exception e) {
            log.debug("{}.{} CHECK 조회 건너뜀: {}", table, column, e.getMessage());
            return;
        }

        for (String name : names) {
            try {
                jdbc.execute("ALTER TABLE " + table + " DROP CONSTRAINT \"" + name + "\"");
                log.info("스키마 보정 — {}.{} CHECK 제약({}) 제거", table, column, name);
            } catch (Exception e) {
                log.debug("{} 제약 제거 건너뜀: {}", name, e.getMessage());
            }
        }
    }
}
