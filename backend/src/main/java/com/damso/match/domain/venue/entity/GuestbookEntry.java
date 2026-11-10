package com.damso.match.domain.venue.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 방명록 한 줄.
 *  - 로그인한 앱 회원만 작성할 수 있고, 목록에는 작성자 이름을 가려서 보여준다.
 *  - 관리자는 부적절한 글을 숨기거나 삭제할 수 있다.
 */
@Entity
@Table(name = "guestbook_entries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GuestbookEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    /** 작성 시점의 작성자 이름 (회원이 이름을 바꿔도 글은 그대로 남는다). */
    @Column(nullable = false, length = 30)
    private String authorName;

    @Column(nullable = false, length = 500)
    private String content;

    /** 관리자가 숨긴 글. 손님 목록에서 제외된다. */
    @Column
    private Boolean hidden;

    /** 소프트 삭제 시각. NULL 이면 정상 글. */
    @Column
    private LocalDateTime deletedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (hidden == null) hidden = Boolean.FALSE;
    }}
