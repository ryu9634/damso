package com.damso.match.global.common;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 서버가 실제로 요청을 처리할 수 있는 상태인지 확인한다.
 * 프로세스 생존뿐 아니라 DB 까지 닿는지를 보고, DB 가 죽었으면 503 을 준다.
 */
@Slf4j
@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
public class HealthController {

    private final JdbcTemplate jdbc;

    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        try {
            jdbc.queryForObject("SELECT 1", Integer.class);
            return ResponseEntity.ok(Map.of("status", "UP", "db", "UP"));
        } catch (Exception e) {
            // 원인은 로그로만 남긴다. 응답에 넣으면 DB 접속 정보가 밖으로 새어 나간다.
            log.error("헬스체크 실패 — DB 조회 불가", e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("status", "DOWN", "db", "DOWN"));
        }
    }
}