package com.damso.match.domain.dating.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 셀렉팅 정기 작업.
 *  - 답변 기한(72시간)이 지난 좋아요를 닫는다.
 *    목록을 열 때도 같은 정리가 돌지만, 아무도 앱을 열지 않는 동안에도 상태가 맞도록 주기적으로 돈다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DatingScheduler {

    private final DatingService datingService;

    /** 10분마다 — 기한이 지난 좋아요 정리. */
    @Scheduled(fixedDelay = 10 * 60 * 1000L, initialDelay = 60 * 1000L)
    public void expireLikes() {
        try {
            int n = datingService.expireStaleLikes();
            if (n > 0) log.info("답변 기한이 지난 좋아요 {}건을 닫았습니다.", n);
        } catch (Exception e) {
            log.warn("좋아요 만료 처리 실패: {}", e.getMessage());
        }
    }
}
