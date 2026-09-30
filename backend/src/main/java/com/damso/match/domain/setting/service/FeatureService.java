package com.damso.match.domain.setting.service;

import com.damso.match.api.app.home.dto.AppFeatureDto;
import com.damso.match.domain.game.entity.GameType;
import static com.damso.match.domain.setting.service.SettingsCatalog.*;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 담소 소개팅 · 담소 셀렉팅 사용 여부.
 *  - 관리자가 필요할 때만 켜서 쓸 수 있도록 기능 단위로 잠근다.
 *  - 화면에서 감추는 것만으로는 URL 직접 접근을 막지 못하므로 API 에서도 확인한다.
 */
@Service
@RequiredArgsConstructor
public class FeatureService {

    private final SettingService settings;

    public boolean matchingEnabled() {
        return !USE_NO.equals(settings.get(FEATURE_MATCHING));
    }

    public boolean selectingEnabled() {
        return !USE_NO.equals(settings.get(FEATURE_SELECTING));
    }

    public boolean gameRoomEnabled() {
        return !USE_NO.equals(settings.get(FEATURE_GAMEROOM));
    }

    /** 홈 화면에 게임방 카드를 띄울지. 기능이 꺼져 있으면 당연히 안 띄운다. */
    public boolean gameRoomOnHome() {
        return gameRoomEnabled() && USE_YES.equals(settings.get(FEATURE_GAMEROOM_HOME));
    }

    /** 게임 종류별 노출 여부. 방은 열려 있어도 특정 게임만 내려둘 수 있다. */
    public boolean gameEnabled(GameType type) {
        String key = switch (type) {
            case BALANCE -> GAME_BALANCE_ENABLED;
            case PICK -> GAME_PICK_ENABLED;
            case LIAR -> GAME_LIAR_ENABLED;
            case MAFIA -> GAME_MAFIA_ENABLED;
        };
        return !USE_NO.equals(settings.get(key));
    }

    public AppFeatureDto forApp() {
        return new AppFeatureDto(
                new AppFeatureDto.Feature(matchingEnabled(), settings.get(FEATURE_MATCHING_NOTICE)),
                new AppFeatureDto.Feature(selectingEnabled(), settings.get(FEATURE_SELECTING_NOTICE)),
                new AppFeatureDto.Feature(gameRoomEnabled(), settings.get(FEATURE_GAMEROOM_NOTICE)),
                gameRoomOnHome()
        );
    }

    /** 담소 소개팅 API 앞단 가드. 꺼져 있으면 안내 문구와 함께 막는다. */
    public void requireMatching() {
        if (!matchingEnabled()) {
            throw new IllegalStateException(noticeOr(settings.get(FEATURE_MATCHING_NOTICE),
                    "담소 소개팅은 지금 이용할 수 없어요."));
        }
    }

    /** 게임방 API 앞단 가드. */
    public void requireGameRoom() {
        if (!gameRoomEnabled()) {
            throw new IllegalStateException(noticeOr(settings.get(FEATURE_GAMEROOM_NOTICE),
                    "게임방은 지금 이용할 수 없어요."));
        }
    }

    /** 담소 셀렉팅 API 앞단 가드. */
    public void requireSelecting() {
        if (!selectingEnabled()) {
            throw new IllegalStateException(noticeOr(settings.get(FEATURE_SELECTING_NOTICE),
                    "담소 셀렉팅은 지금 이용할 수 없어요."));
        }
    }

    private static String noticeOr(String notice, String fallback) {
        return notice == null || notice.isBlank() ? fallback : notice.trim();
    }
}
