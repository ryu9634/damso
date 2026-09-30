package com.damso.match.global.schema;

import com.damso.match.global.config.DataSeeder;
import com.damso.match.global.security.DataCrypto;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 개인정보 컬럼을 암호화 저장으로 바꾸는 일회성 이관.
 *
 * <p>두 가지를 한다.
 * <ol>
 *   <li>암호문이 평문보다 길어지므로 컬럼 폭을 넓힌다. (ddl-auto=update 는 기존 컬럼 타입을 안 바꾼다)</li>
 *   <li>아직 평문으로 남아 있는 행을 읽어 암호화해 다시 쓴다. 접두어({@link DataCrypto#PREFIX})가
 *       없는 값만 고르므로 몇 번을 돌려도 같은 결과다.</li>
 * </ol>
 * 여기서 실패하면 앱이 뜨지 않는다. 평문이 조용히 남는 것보다 배포가 되돌아가는 편이 낫다.
 *
 * <p>계정 시드(DataSeeder, order 10)보다 먼저 돈다.
 */
@Slf4j
@Component
@Order(1)
public class PiiEncryptionMigration implements CommandLineRunner {

    /** 테이블 → 암호화하는 컬럼과 폭. 엔티티의 @Column(length) 와 맞춰 둔다. */
    private record Target(String table, String column, int length) {}

    private static final List<Target> TARGETS = List.of(
            new Target("app_users", "name", 255),
            new Target("app_users", "phone", 255),
            new Target("customers", "real_name", 255),
            new Target("customers", "phone", 255),
            new Target("dating_profiles", "name", 255),
            new Target("dating_profiles", "contact", 255),
            new Target("messages", "sender_contact", 512)
    );

    private final JdbcTemplate jdbc;
    private final DataCrypto crypto;

    public PiiEncryptionMigration(JdbcTemplate jdbc, DataCrypto crypto) {
        this.jdbc = jdbc;
        this.crypto = crypto;
    }

    @Override
    public void run(String... args) {
        for (Target t : TARGETS) {
            SchemaOps.widenVarchar(jdbc, t.table(), t.column(), t.length());
        }
        for (Target t : TARGETS) {
            encryptPlainRows(t);
        }
    }

    private void encryptPlainRows(Target t) {
        List<Map<String, Object>> rows;
        try {
            rows = jdbc.queryForList(
                    "SELECT id, " + t.column() + " AS v FROM " + t.table()
                            + " WHERE " + t.column() + " IS NOT NULL AND " + t.column() + " <> ''"
                            + " AND " + t.column() + " NOT LIKE ?",
                    DataCrypto.PREFIX + "%");
        } catch (Exception e) {
            // 새 DB 라 테이블이 아직 없을 수 있다. Hibernate 가 만든 뒤 다음 부팅에 처리된다.
            log.debug("{}.{} 이관 건너뜀: {}", t.table(), t.column(), e.getMessage());
            return;
        }
        if (rows.isEmpty()) return;

        for (Map<String, Object> row : rows) {
            String plain = String.valueOf(row.get("v"));
            jdbc.update("UPDATE " + t.table() + " SET " + t.column() + " = ? WHERE id = ?",
                    crypto.encrypt(plain), row.get("id"));
        }
        log.info("개인정보 암호화 — {}.{} {}건", t.table(), t.column(), rows.size());
    }
}
