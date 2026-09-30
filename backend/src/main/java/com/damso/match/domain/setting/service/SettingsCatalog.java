package com.damso.match.domain.setting.service;

import java.util.List;

/**
 * 관리자 환경설정 항목 정의(카탈로그).
 *  - 여기에 항목을 추가하면 관리자 화면에 자동으로 입력칸이 생긴다. (UI 코드 수정 불필요)
 *  - key 는 저장/조회에, group/label/type 은 화면 렌더링에 쓰인다.
 */
public final class SettingsCatalog {

    private SettingsCatalog() {}

    public enum Type { NUMBER, TEXT, TEXTAREA, SELECT }

    /** SELECT 는 options 중 하나만 저장할 수 있다. 나머지 타입은 options 가 비어 있다. */
    public record Field(String key, String label, String group, Type type, String defaultValue, List<String> options) {
        public Field(String key, String label, String group, Type type, String defaultValue) {
            this(key, label, group, type, defaultValue, List.of());
        }
    }

    // ─── 설정 키 상수 ────────────────────────────────────
    public static final String MAX_MESSAGE_RECIPIENTS = "max_message_recipients";

    public static final String GUEST_PREFERENCE_TITLE = "guest.preference.title";
    public static final String GUEST_PREFERENCE_SUBTITLE = "guest.preference.subtitle";
    public static final String GUEST_MESSAGE_TITLE = "guest.message.title";
    public static final String GUEST_MESSAGE_SUBTITLE = "guest.message.subtitle";
    public static final String GUEST_WAITING_TITLE = "guest.waiting.title";
    public static final String GUEST_WAITING_SUBTITLE = "guest.waiting.subtitle";
    public static final String GUEST_END_TITLE = "guest.end.title";
    public static final String GUEST_END_SUBTITLE = "guest.end.subtitle";

    // ─── 앱 홈 화면 ──────────────────────────────────────
    public static final String HOME_OPEN_STATUS = "home.open_status";
    public static final String HOME_HOURS = "home.hours";
    public static final String HOME_NOTICE = "home.notice";
    public static final String HOME_COCKTAIL_NAME = "home.cocktail.name";
    public static final String HOME_COCKTAIL_DESC = "home.cocktail.desc";
    public static final String HOME_COCKTAIL_IMAGE = "home.cocktail.image";
    public static final String HOME_COUPLES_VISIBLE = "home.couples.visible";

    /** 영업여부 선택지. 첫 번째 값이 "영업중"이며 앱에서 초록 배지로 표시된다. */
    public static final String OPEN_STATUS_OPEN = "영업중";
    public static final String OPEN_STATUS_PREPARING = "오픈 준비중";
    public static final String OPEN_STATUS_CLOSED = "오늘 휴무";

    public static final String VISIBLE_YES = "공개";
    public static final String VISIBLE_NO = "비공개";

    // ─── 앱 기능 사용 여부 ───────────────────────────────
    /** 담소 소개팅(현장 진행) 기능 사용 여부. */
    public static final String FEATURE_MATCHING = "feature.matching.enabled";
    public static final String FEATURE_MATCHING_NOTICE = "feature.matching.notice";
    /** 담소 셀렉팅(프로필 열람·선택) 기능 사용 여부. */
    public static final String FEATURE_SELECTING = "feature.selecting.enabled";
    public static final String FEATURE_SELECTING_NOTICE = "feature.selecting.notice";

    public static final String USE_YES = "사용";
    public static final String USE_NO = "미사용";

    // ─── 게임방 ─────────────────────────────────────────
    /** 게임방 자체를 쓸지. 끄면 화면과 API 가 모두 막힌다. */
    public static final String FEATURE_GAMEROOM = "feature.gameroom.enabled";
    /**
     * 홈 화면에 게임방 카드를 띄울지. 기능은 열어두고 안내만 미루고 싶을 때 쓴다.
     * (링크를 아는 사람은 들어올 수 있다)
     */
    public static final String FEATURE_GAMEROOM_HOME = "feature.gameroom.home";
    public static final String FEATURE_GAMEROOM_NOTICE = "feature.gameroom.notice";
    /** 게임 종류별 노출. 방은 열어두고 특정 게임만 잠깐 내릴 수 있다. */
    public static final String GAME_BALANCE_ENABLED = "game.balance.enabled";
    public static final String GAME_PICK_ENABLED = "game.pick.enabled";
    public static final String GAME_LIAR_ENABLED = "game.liar.enabled";
    public static final String GAME_MAFIA_ENABLED = "game.mafia.enabled";

    // ─── 담소 셀렉팅 만남 약속 안내 ──────────────────────
    /** 만나는 장소. 손님이 고르지 않고 여기 적은 곳으로 고정된다. */
    public static final String SELECTING_MEETING_PLACE = "selecting.meeting.place";
    /** 예약금·장소 안내 문구. */
    public static final String SELECTING_MEETING_NOTICE = "selecting.meeting.notice";
    /** 예약 링크 앞에 붙는 안내 한 줄. */
    public static final String SELECTING_BOOKING_NOTICE = "selecting.booking.notice";
    /** 예약(결제) 링크. 비워두면 앱에 링크 버튼이 나오지 않는다. */
    public static final String SELECTING_BOOKING_URL = "selecting.booking.url";

    private static final String G_GENERAL = "운영";
    private static final String G_GUEST = "손님 화면 문구";
    private static final String G_HOME = "앱 홈 화면";
    /** 기능 on/off 는 별도 관리자 메뉴(손님 앱 → 기능 사용)로 뗀다. */
    public static final String G_FEATURE = "앱 기능 사용";
    /** 셀렉팅 약속 안내는 손님 선택 소개팅 메뉴에서 따로 관리한다. */
    public static final String G_SELECTING = "담소 셀렉팅 약속";

    public static final List<Field> FIELDS = List.of(
            new Field(MAX_MESSAGE_RECIPIENTS, "메시지 발송 최대 인원", G_GENERAL, Type.NUMBER, "2"),

            new Field(FEATURE_MATCHING, "담소 소개팅 사용", G_FEATURE, Type.SELECT, USE_YES,
                    List.of(USE_YES, USE_NO)),
            new Field(FEATURE_MATCHING_NOTICE, "담소 소개팅 · 미사용 안내 문구", G_FEATURE, Type.TEXTAREA,
                    "담소 소개팅은 지금 운영하고 있지 않아요. 다음 일정은 공지로 알려드릴게요."),
            new Field(FEATURE_SELECTING, "담소 셀렉팅 사용", G_FEATURE, Type.SELECT, USE_YES,
                    List.of(USE_YES, USE_NO)),
            new Field(FEATURE_SELECTING_NOTICE, "담소 셀렉팅 · 미사용 안내 문구", G_FEATURE, Type.TEXTAREA,
                    "담소 셀렉팅은 지금 잠시 쉬어가는 중이에요. 곧 다시 열어드릴게요."),

            new Field(HOME_OPEN_STATUS, "영업여부", G_HOME, Type.SELECT, OPEN_STATUS_OPEN,
                    List.of(OPEN_STATUS_OPEN, OPEN_STATUS_PREPARING, OPEN_STATUS_CLOSED)),
            new Field(HOME_HOURS, "영업시간 안내", G_HOME, Type.TEXT, "오후 6시 ~ 새벽 2시"),
            new Field(HOME_NOTICE, "홈 공지 문구", G_HOME, Type.TEXTAREA, ""),
            // 아래 세 항목은 '술 메뉴'에서 오늘의 추천을 켜두지 않았을 때만 쓰인다.
            new Field(HOME_COCKTAIL_NAME, "추천 한 잔(예비) · 이름", G_HOME, Type.TEXT, "오늘의 담소 하이볼"),
            new Field(HOME_COCKTAIL_DESC, "추천 한 잔(예비) · 설명", G_HOME, Type.TEXTAREA,
                    "주인장이 오늘 직접 고른 한 잔이에요."),
            new Field(HOME_COCKTAIL_IMAGE, "추천 한 잔(예비) · 사진 주소", G_HOME, Type.TEXT, ""),
            new Field(HOME_COUPLES_VISIBLE, "성사 커플 공개 여부", G_HOME, Type.SELECT, VISIBLE_YES,
                    List.of(VISIBLE_YES, VISIBLE_NO)),

            new Field(FEATURE_GAMEROOM, "게임방 사용", G_FEATURE, Type.SELECT, USE_YES,
                    List.of(USE_YES, USE_NO)),
            // 기본은 감춤 — 준비가 끝나면 관리자가 켠다.
            new Field(FEATURE_GAMEROOM_HOME, "게임방 · 홈 화면에 노출", G_FEATURE, Type.SELECT, USE_NO,
                    List.of(USE_YES, USE_NO)),
            new Field(FEATURE_GAMEROOM_NOTICE, "게임방 · 미사용 안내 문구", G_FEATURE, Type.TEXTAREA,
                    "게임방은 지금 열려 있지 않아요. 다음에 다시 만나요!"),
            new Field(GAME_BALANCE_ENABLED, "게임 · 밸런스 게임 사용", G_FEATURE, Type.SELECT, USE_YES,
                    List.of(USE_YES, USE_NO)),
            new Field(GAME_PICK_ENABLED, "게임 · 랜덤 뽑기 사용", G_FEATURE, Type.SELECT, USE_YES,
                    List.of(USE_YES, USE_NO)),
            new Field(GAME_LIAR_ENABLED, "게임 · 라이어 게임 사용", G_FEATURE, Type.SELECT, USE_YES,
                    List.of(USE_YES, USE_NO)),
            new Field(GAME_MAFIA_ENABLED, "게임 · 마피아 사용", G_FEATURE, Type.SELECT, USE_YES,
                    List.of(USE_YES, USE_NO)),

            new Field(SELECTING_MEETING_PLACE, "만나는 장소", G_SELECTING, Type.TEXT, "담소혼술바"),
            new Field(SELECTING_MEETING_NOTICE, "약속 안내 문구", G_SELECTING, Type.TEXTAREA,
                    "장소는 담소혼술바에서 진행될 예정입니다!\n"
                            + "노쇼 방지를 위해 15000원씩 받고있고, 예약금은 현장에서 사용 가능합니다!"),
            new Field(SELECTING_BOOKING_NOTICE, "예약 링크 안내 문구", G_SELECTING, Type.TEXT,
                    "아래 예약 링크로 예약 주시면 확정됩니다!"),
            new Field(SELECTING_BOOKING_URL, "예약(결제) 링크", G_SELECTING, Type.TEXT,
                    "https://map.naver.com/p/entry/place/2006044431?placePath=/booking"),

            new Field(GUEST_PREFERENCE_TITLE, "호감도 지목 · 제목", G_GUEST, Type.TEXT, "호감 가는 상대를 골라주세요"),
            new Field(GUEST_PREFERENCE_SUBTITLE, "호감도 지목 · 안내", G_GUEST, Type.TEXTAREA, "호감 가는 상대를 1·2·3순위로 골라주세요. (선택 안 해도 됩니다)"),
            new Field(GUEST_MESSAGE_TITLE, "메시지 · 제목", G_GUEST, Type.TEXT, "마음을 전해보세요"),
            new Field(GUEST_MESSAGE_SUBTITLE, "메시지 · 안내", G_GUEST, Type.TEXTAREA, "오늘 만난 사람에게 마음을 전해보세요."),
            new Field(GUEST_WAITING_TITLE, "대기 · 제목", G_GUEST, Type.TEXT, "등록이 완료됐어요!"),
            new Field(GUEST_WAITING_SUBTITLE, "대기 · 안내", G_GUEST, Type.TEXTAREA, "다른 손님 입장을 기다리는 중입니다."),
            new Field(GUEST_END_TITLE, "종료 · 제목", G_GUEST, Type.TEXT, "담소 소개팅 운영시간 아니에요~"),
            new Field(GUEST_END_SUBTITLE, "종료 · 안내", G_GUEST, Type.TEXTAREA,
                    "운영요일 : 금요일, 토요일\n운영시간 : 19:00~21:00\n[예약링크 바로가기](https://map.naver.com/p/entry/place/2006044431?placePath=/booking)")
    );

    /**
     * 관리자 화면에서 편집 가능한 항목.
     *  - 손님 화면 문구(G_GUEST)는 노출하지 않고 기본값으로만 사용한다.
     *  - 기본값 해석(byKey/get)과 손님 화면은 여전히 전체 FIELDS 를 사용한다.
     */
    public static final List<Field> ADMIN_FIELDS = FIELDS.stream()
            .filter(f -> !f.group().equals(G_GUEST))
            .toList();

    public static Field byKey(String key) {
        return FIELDS.stream().filter(f -> f.key().equals(key)).findFirst().orElse(null);
    }
}
