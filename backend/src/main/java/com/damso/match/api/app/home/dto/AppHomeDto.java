package com.damso.match.api.app.home.dto;

import com.damso.match.api.admin.venue.dto.DrinkItemDto;

import java.time.LocalDate;
import java.util.List;

/**
 * 앱 홈 화면에 필요한 값 묶음.
 *  - 영업여부·공지: 관리자 환경설정(app_settings)에서 온다.
 *  - 추천 한 잔 / 술 메뉴: 관리자가 등록한 술 메뉴(drink_items). 등록된 게 없으면
 *    환경설정에 적어둔 추천 칵테일 문구를 그대로 쓴다.
 *  - 성사 커플: 소개팅 매칭(dating_matches) 중 '성사' 단계인 페어를 익명화해 보여준다.
 *  - features: 담소 소개팅·셀렉팅을 지금 쓸 수 있는지. 홈 버튼 노출에 쓴다.
 *  - matching: 지금 진행 중인 담소 소개팅. 진행 중일 때만 홈에 참여 영역이 뜬다.
 */
public record AppHomeDto(
        String openStatus,
        boolean open,
        String hours,
        String notice,
        Cocktail cocktail,
        List<DrinkItemDto> drinks,
        Couples couples,
        AppFeatureDto features,
        Matching matching
) {
    /**
     * 지금 진행 중인 담소 소개팅.
     *  - live: 기능이 켜져 있고 이벤트가 종료 전이면 true. false 면 나머지는 비어 있다.
     *  - registered: 로그인한 회원이 이번 이벤트에 이미 참여 등록했는지.
     */
    public record Matching(
            boolean live,
            Long eventId,
            String name,
            String status,
            String statusLabel,
            int participants,
            boolean registered
    ) {
        public static Matching none() {
            return new Matching(false, null, null, null, null, 0, false);
        }
    }

    /** 오늘의 추천 한 잔. 사진이 없으면 이모지로 그린다. */
    public record Cocktail(String name, String description, String imageUrl, String emoji) {}

    public record Couples(boolean visible, int total, List<Couple> recent) {}

    /** 익명화된 성사 커플 한 쌍. (예: 김○ ♥ 이○) */
    public record Couple(String label, LocalDate since) {}
}
