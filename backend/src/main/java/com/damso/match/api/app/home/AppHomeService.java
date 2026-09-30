package com.damso.match.api.app.home;

import com.damso.match.api.admin.venue.dto.DrinkItemDto;
import com.damso.match.api.app.home.dto.AppHomeDto;
import com.damso.match.domain.dating.entity.DatingMatch;
import com.damso.match.domain.dating.entity.DatingMatchStage;
import com.damso.match.domain.dating.entity.DatingProfile;
import com.damso.match.domain.dating.repository.DatingMatchRepository;
import com.damso.match.domain.dating.repository.DatingProfileRepository;
import com.damso.match.domain.event.entity.Event;
import com.damso.match.domain.event.entity.EventStatus;
import com.damso.match.domain.event.repository.CustomerRepository;
import com.damso.match.domain.event.repository.EventRepository;
import com.damso.match.domain.setting.service.FeatureService;
import com.damso.match.domain.setting.service.SettingService;
import com.damso.match.domain.venue.entity.DrinkItem;
import com.damso.match.domain.venue.service.DrinkService;
import static com.damso.match.domain.setting.service.SettingsCatalog.*;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** 앱 홈 화면 데이터. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AppHomeService {

    /** 홈에 보여줄 성사 커플 최대 개수. */
    private static final int RECENT_COUPLE_LIMIT = 5;

    private final SettingService settings;
    private final DatingMatchRepository matchRepo;
    private final DatingProfileRepository profileRepo;
    private final DrinkService drinkService;
    private final FeatureService featureService;
    private final EventRepository eventRepo;
    private final CustomerRepository customerRepo;

    public AppHomeDto home(Long userId) {
        String status = settings.get(HOME_OPEN_STATUS);
        boolean couplesVisible = VISIBLE_YES.equals(settings.get(HOME_COUPLES_VISIBLE));

        return new AppHomeDto(
                status,
                OPEN_STATUS_OPEN.equals(status),
                settings.get(HOME_HOURS),
                settings.get(HOME_NOTICE),
                cocktail(),
                drinkService.listVisible().stream().map(DrinkItemDto::from).toList(),
                couples(couplesVisible),
                featureService.forApp(),
                matching(userId)
        );
    }

    /**
     * 지금 진행 중인 담소 소개팅.
     * 기능이 꺼져 있거나, 이벤트가 없거나, 마지막 이벤트가 이미 끝났으면 '없음'이다.
     * (마지막 이벤트를 새로 만들지는 않는다 — 홈을 여는 것만으로 이벤트가 생기면 안 된다)
     */
    private AppHomeDto.Matching matching(Long userId) {
        if (!featureService.matchingEnabled()) return AppHomeDto.Matching.none();
        Event e = eventRepo.findTopByOrderByIdDesc().orElse(null);
        if (e == null || e.getStatus() == null || e.getStatus() == EventStatus.CLOSED) {
            return AppHomeDto.Matching.none();
        }
        boolean registered = userId != null
                && customerRepo.findByEventIdAndAppUserId(e.getId(), userId).isPresent();
        return new AppHomeDto.Matching(
                true, e.getId(), e.getName(), e.getStatus().name(), statusLabel(e.getStatus()),
                customerRepo.findByEventIdOrderByCreatedAtAsc(e.getId()).size(),
                registered);
    }

    /** 손님에게 보여줄 진행 단계 문구. */
    private static String statusLabel(EventStatus s) {
        return switch (s) {
            case REGISTRATION -> "지금 참여 등록 받는 중";
            case ROUND_1 -> "1차 자리 진행 중";
            case ROUND_2 -> "2차 자리 진행 중";
            case ROUND_3 -> "3차 자리 진행 중";
            case PREFERENCE -> "호감 상대 지목 중";
            case ROUND_4 -> "4차 자리 진행 중";
            case MESSAGE -> "마지막 메시지 전달 중";
            case CLOSED -> "종료";
        };
    }

    /**
     * 오늘의 추천 한 잔.
     * 관리자가 술 메뉴에서 '오늘의 추천'을 켜뒀으면 그 메뉴를, 아니면 환경설정 문구를 쓴다.
     */
    private AppHomeDto.Cocktail cocktail() {
        DrinkItem pick = drinkService.recommended();
        if (pick != null) {
            return new AppHomeDto.Cocktail(
                    pick.getName(), pick.getDescription(), pick.getImageUrl(), pick.getEmoji());
        }
        return new AppHomeDto.Cocktail(
                settings.get(HOME_COCKTAIL_NAME),
                settings.get(HOME_COCKTAIL_DESC),
                blankToNull(settings.get(HOME_COCKTAIL_IMAGE)),
                null);
    }

    private AppHomeDto.Couples couples(boolean visible) {
        List<DatingMatch> successes = matchRepo.findAll().stream()
                .filter(m -> DatingMatchStage.orDefault(m.getStage()) == DatingMatchStage.SUCCESS)
                .sorted(Comparator.comparing(
                        (DatingMatch m) -> m.getUpdatedAt() == null ? m.getCreatedAt() : m.getUpdatedAt(),
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        if (!visible) return new AppHomeDto.Couples(false, successes.size(), List.of());

        Map<Long, DatingProfile> byId = profileRepo.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(DatingProfile::getId, Function.identity(), (a, b) -> a));

        List<AppHomeDto.Couple> recent = successes.stream()
                .limit(RECENT_COUPLE_LIMIT)
                .map(m -> {
                    LocalDateTime when = m.getUpdatedAt() == null ? m.getCreatedAt() : m.getUpdatedAt();
                    return new AppHomeDto.Couple(
                            maskName(byId.get(m.getProfileAId())) + " ♥ " + maskName(byId.get(m.getProfileBId())),
                            when == null ? null : when.toLocalDate());
                })
                .toList();

        return new AppHomeDto.Couples(true, successes.size(), recent);
    }

    /** 홍길동 → 홍○○. 손님 화면에는 실명을 그대로 노출하지 않는다. */
    private static String maskName(DatingProfile p) {
        String name = p == null ? null : p.getName();
        if (name == null || name.isBlank()) return "○○";
        String trimmed = name.trim();
        if (trimmed.length() == 1) return trimmed + "○";
        return trimmed.charAt(0) + "○".repeat(trimmed.length() - 1);
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v;
    }
}
