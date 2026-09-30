package com.damso.match.domain.venue.repository;

import com.damso.match.domain.venue.entity.GuestbookEntry;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GuestbookEntryRepository extends JpaRepository<GuestbookEntry, Long> {

    /** 손님 화면용 — 숨김·삭제 글 제외. */
    List<GuestbookEntry> findByDeletedAtIsNullAndHiddenFalseOrderByCreatedAtDesc();

    /** 관리자용 — 숨김 글 포함, 삭제 글만 제외. */
    List<GuestbookEntry> findByDeletedAtIsNullOrderByCreatedAtDesc();
}
