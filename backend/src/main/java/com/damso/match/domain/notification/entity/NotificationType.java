package com.damso.match.domain.notification.entity;

/** 알림 종류. 앱에서 아이콘·색을 고르는 데 쓴다. */
public enum NotificationType {
    /** 누군가 나에게 좋아요를 보냈다. */
    LIKE_RECEIVED,
    /** 내가 보낸 좋아요에 상대가 좋아요로 답했다. */
    LIKE_ACCEPTED,
    /** 내가 보낸 좋아요에 상대가 미안해요로 답했다. */
    LIKE_DECLINED,
    /** 상대가 만남 약속을 제안했다. */
    MEETING_PROPOSED,
    /** 만남 약속이 확정됐다. */
    MEETING_CONFIRMED,
    /** 만남 약속이 취소됐다. */
    MEETING_CANCELED,
    /** 그 밖의 안내. */
    NOTICE
}
