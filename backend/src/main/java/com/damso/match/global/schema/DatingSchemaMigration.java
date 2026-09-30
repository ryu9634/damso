package com.damso.match.global.schema;

import com.damso.match.domain.dating.service.DatingQuestionService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 소개팅 테이블 스키마 보정. (ddl-auto=update 가 처리하지 못하는 부분만)
 *  1) 상세 항목을 커스텀 질문으로 이전하면서 남은 레거시 컬럼의 NOT NULL 완화.
 *  2) 관리 컬럼(status) 백필 — 기존 행은 NULL 이므로 PENDING 으로 채우고, 레거시 NEW 값도 PENDING 으로 옮긴다.
 *  3) 좋아요 답변 상태(dating_likes.status) 백필 — 좋아요/미안해요 기능 이전에 쌓인 기록을 살린다.
 *  - 질문 시드/이전(DatingQuestionService)보다 먼저 실행되도록 우선순위를 높인다.
 */
@Slf4j
@Component
@Order(0)
public class DatingSchemaMigration implements CommandLineRunner {

    private static final String[] LEGACY_COLS = {
            "height", "weight", "mbti", "personality", "hobbies", "strengths", "weaknesses"
    };

    private final JdbcTemplate jdbc;

    public DatingSchemaMigration(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(String... args) {
        for (String col : LEGACY_COLS) {
            SchemaOps.dropNotNull(jdbc, "dating_profiles", col);
        }

        // 좋아요 답변에 EXPIRED(기간 만료)가 추가됐다. 기존 테이블의 낡은 CHECK 제약을 지운다.
        SchemaOps.dropEnumCheck(jdbc, "dating_likes", "status");

        // 관리 상태 백필: 기존 프로필은 '심사중'으로 시작
        backfill("UPDATE dating_profiles SET status = 'PENDING' WHERE status IS NULL", "status");
        // 레거시 값 이전: NEW(신규) → PENDING(심사중). dating_matches.stage 의 NEW 와는 별개.
        backfill("UPDATE dating_profiles SET status = 'PENDING' WHERE status = 'NEW'", "status(NEW→PENDING)");
        backfill("UPDATE dating_matches SET stage = 'NEW' WHERE stage IS NULL", "stage");

        // 좋아요 답변 상태: 기존 기록은 '답변 대기'로 두되, 이미 쌍방이던 페어는 '좋아요'로 본다.
        backfill("UPDATE dating_likes SET status = 'PENDING' WHERE status IS NULL",
                "좋아요 status");
        backfill("UPDATE dating_likes l SET status = 'ACCEPTED' "
                        + "WHERE l.status = 'PENDING' AND EXISTS ("
                        + "  SELECT 1 FROM dating_likes r "
                        + "  WHERE r.from_profile_id = l.to_profile_id "
                        + "    AND r.to_profile_id = l.from_profile_id)",
                "쌍방 좋아요 ACCEPTED");

        // 약속 상태: 관리자가 잡아둔 날짜가 있으면 확정으로 본다.
        backfill("UPDATE dating_pair_notes SET appointment_status = 'CONFIRMED' "
                        + "WHERE appointment_status IS NULL AND date_day IS NOT NULL",
                "약속 CONFIRMED");
        backfill("UPDATE dating_pair_notes SET appointment_status = 'NONE' "
                        + "WHERE appointment_status IS NULL", "약속 status");
    }

    private void backfill(String sql, String what) {
        try {
            int n = jdbc.update(sql);
            if (n > 0) log.info("소개팅 {} 백필: {}건", what, n);
        } catch (Exception e) {
            log.debug("소개팅 {} 백필 건너뜀: {}", what, e.getMessage());
        }
    }
}