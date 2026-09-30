package com.damso.match.api.app.home.dto;

/**
 * 손님 앱에서 지금 쓸 수 있는 기능.
 *  - 관리자 환경설정(앱 기능 사용)에서 켜고 끈다.
 *  - 꺼져 있으면 앱은 하단 탭을 감추고, 서버도 해당 API 를 막는다.
 */
public record AppFeatureDto(
        Feature matching,
        Feature selecting,
        Feature gameRoom,
        /** 홈 화면에 게임방 카드를 띄울지. 기능 사용과 따로 켠다. */
        boolean gameRoomOnHome
) {
    /** enabled=false 면 notice 문구를 대신 보여준다. */
    public record Feature(boolean enabled, String notice) {}
}
