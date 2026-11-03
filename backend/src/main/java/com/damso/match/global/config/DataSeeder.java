package com.damso.match.global.config;

import com.damso.match.domain.admin.service.AdminAuthService;
import com.damso.match.domain.game.service.GameService;
import com.damso.match.domain.member.service.AuthService;
import com.damso.match.domain.venue.service.DrinkService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 첫 실행 시 필요한 계정을 만들어 둔다. (이미 있으면 건드리지 않는다)
 *  - 관리자: admin / admin  → 로그인 후 비밀번호를 꼭 바꾸세요.
 *  - 손님 테스트 계정: test / test1234
 *  - 앱 홈 술 메뉴 / 게임 문항: 하나도 없을 때만 기본값을 넣는다.
 *    운영에서 빼려면 application.yml 에 damso.seed-test-user: false 를 넣는다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements ApplicationRunner {

    private static final String TEST_LOGIN_ID = "test";
    private static final String TEST_PASSWORD = "test1234";

    private final AdminAuthService adminAuthService;
    private final AuthService authService;
    private final DrinkService drinkService;
    private final GameService gameService;

    @Value("${damso.seed-test-user:true}")
    private boolean seedTestUser;

    @Override
    public void run(ApplicationArguments args) {
        adminAuthService.ensureDefaultAccount();
        drinkService.seedDefaultsIfEmpty();
        gameService.seedDefaultsIfEmpty();

        if (seedTestUser) {
            authService.ensureUser(
                    TEST_LOGIN_ID, TEST_PASSWORD,
                    "테스트회원", "테스트", LocalDate.of(1995, 1, 1), "01000000000");
            log.info("손님 테스트 계정 준비 완료 — 아이디 {} / 비밀번호 {}", TEST_LOGIN_ID, TEST_PASSWORD);
        }
    }
}
