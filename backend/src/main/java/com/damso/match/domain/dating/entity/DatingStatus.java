package com.damso.match.domain.dating.entity;

/**
 * 소개팅 회원 관리 상태.
 *  - 관리자가 "이 사람을 어디까지 처리했는지" 추적하기 위한 값.
 *  - 심사중·휴면·제외는 셀렉팅에서 빠진다. 남의 목록에 뜨지도 않고, 본인도 열람·선택할 수 없다.
 *    (한쪽만 막으면 답이 오지 않을 상대에게 좋아요를 보내게 된다)
 */
public enum DatingStatus {
    /** 심사중 — 신규 등록 후 관리자 검토 전. */
    PENDING("심사중", "프로필 심사중입니다. 잠시만 기다려주세요!"),
    /** 검토 완료, 소개 가능. */
    ACTIVE("활동중", null),
    /** 소개 진행 중. */
    MATCHING("소개진행", null),
    /** 매칭 성사. */
    MATCHED("성사", null),
    /** 휴면(연락 두절 등). */
    DORMANT("휴면", "지금은 휴면 상태예요. 다시 이용하시려면 담소에 문의해주세요."),
    /** 제외 — 셀렉팅 이용 불가. */
    EXCLUDED("제외", "지금은 담소 셀렉팅을 이용할 수 없어요. 궁금한 점은 담소에 문의해주세요.");

    private final String label;
    private final String notice;

    DatingStatus(String label, String notice) {
        this.label = label;
        this.notice = notice;
    }

    public String label() {
        return label;
    }

    /** 이용할 수 없는 상태일 때 손님에게 보여줄 안내. 이용 가능하면 null. */
    public String notice() {
        return notice;
    }

    public static DatingStatus orDefault(DatingStatus s) {
        return s == null ? PENDING : s;
    }

    /**
     * 셀렉팅을 이용할 수 있는 상태인지.
     * 남의 열람 목록에 노출되는 조건이자, 본인이 이성 프로필을 보고 고를 수 있는 조건이다.
     */
    public boolean isBrowsable() {
        return notice == null;
    }
}
