package com.damso.match.domain.dating.entity;

/**
 * 받은 좋아요에 대한 답. 보낸 직후는 PENDING 이고, 받은 사람이 답하면 바뀐다.
 *  - PENDING  : 아직 답하지 않음
 *  - ACCEPTED : 좋아요 (서로 마음이 맞았으니 만남 약속을 잡을 수 있다)
 *  - DECLINED : 미안해요
 *  - EXPIRED  : 답변 기한(72시간)이 지나 자동으로 닫힘
 */
public enum DatingLikeStatus {
    PENDING("답변 대기"),
    ACCEPTED("좋아요"),
    DECLINED("미안해요"),
    EXPIRED("기간 만료");

    private final String label;

    DatingLikeStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** 값이 없거나 모르는 값이면 대기중으로 본다. (기존 데이터 호환) */
    public static DatingLikeStatus orDefault(DatingLikeStatus s) {
        return s == null ? PENDING : s;
    }
}
