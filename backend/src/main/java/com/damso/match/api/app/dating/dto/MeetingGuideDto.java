package com.damso.match.api.app.dating.dto;

import java.util.List;

/**
 * 만남 약속 화면에 함께 보여주는 안내 (관리자 환경설정에서 온다).
 *  - place: 만나는 장소. 손님은 고르지 않고 이 값으로 고정된다.
 *  - notice: 예약금·장소 안내 문구.
 *  - bookingNotice / bookingUrl: 예약(결제) 링크 안내. url 이 비어 있으면 앱에 버튼을 띄우지 않는다.
 *  - hours / maxSlots: 고를 수 있는 시간대와 최대 선택 개수.
 */
public record MeetingGuideDto(
        String place,
        String notice,
        String bookingNotice,
        String bookingUrl,
        List<Integer> hours,
        int maxSlots
) {}
