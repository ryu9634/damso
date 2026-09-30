package com.damso.match.domain.venue.dto;

import com.damso.match.domain.venue.entity.GuestbookEntry;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/** 방명록 글 (손님 화면). 작성자는 본인이 정한 닉네임으로만 표시된다. */
public record GuestbookDto(
        Long id,
        String authorName,
        String content,
        LocalDateTime createdAt,
        boolean mine
) {
    /** displayName 은 글쓴이의 현재 닉네임(탈퇴 등으로 없으면 작성 시점 이름). */
    public static GuestbookDto from(GuestbookEntry e, String displayName, Long viewerId) {
        return new GuestbookDto(
                e.getId(),
                displayName == null || displayName.isBlank() ? "익명" : displayName,
                e.getContent(),
                e.getCreatedAt(),
                viewerId != null && viewerId.equals(e.getUserId())
        );
    }

    /** 방명록 작성 요청. */
    public record WriteRequest(
            @NotBlank(message = "내용을 입력해주세요")
            @Size(max = 500, message = "방명록은 최대 500자까지 쓸 수 있어요")
            String content
    ) {}

    /** 관리자용 — 닉네임과 함께 실명·숨김 여부까지 보여준다. */
    public record AdminView(
            Long id,
            Long userId,
            String authorName,
            /** 회원 실명. 탈퇴 회원이면 null. */
            String realName,
            String content,
            boolean hidden,
            LocalDateTime createdAt
    ) {
        public static AdminView from(GuestbookEntry e, String displayName, String realName) {
            return new AdminView(
                    e.getId(), e.getUserId(),
                    displayName == null || displayName.isBlank() ? e.getAuthorName() : displayName,
                    realName,
                    e.getContent(),
                    Boolean.TRUE.equals(e.getHidden()),
                    e.getCreatedAt());
        }
    }
}
