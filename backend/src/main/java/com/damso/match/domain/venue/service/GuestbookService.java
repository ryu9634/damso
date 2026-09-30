package com.damso.match.domain.venue.service;

import com.damso.match.domain.member.entity.AppUser;
import com.damso.match.domain.member.repository.AppUserRepository;
import com.damso.match.domain.venue.dto.GuestbookDto;
import com.damso.match.domain.venue.entity.GuestbookEntry;
import com.damso.match.domain.venue.repository.GuestbookEntryRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 방명록 — 로그인한 회원만 작성, 관리자는 숨김/삭제. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GuestbookService {

    /** 한 회원이 하루에 남길 수 있는 글 수. */
    private static final int DAILY_LIMIT = 5;

    private final GuestbookEntryRepository repo;
    private final AppUserRepository userRepo;

    public List<GuestbookDto> listForApp(AppUser viewer) {
        Long viewerId = viewer == null ? null : viewer.getId();
        List<GuestbookEntry> entries = repo.findByDeletedAtIsNullAndHiddenFalseOrderByCreatedAtDesc();
        Map<Long, AppUser> authors = authorsOf(entries);
        return entries.stream()
                .map(e -> GuestbookDto.from(e, displayNameOf(e, authors), viewerId))
                .toList();
    }

    @Transactional
    public GuestbookDto write(AppUser user, GuestbookDto.WriteRequest req) {
        if (!user.isProfileComplete()) {
            throw new IllegalArgumentException("마이페이지에서 닉네임·성명·생년월일을 먼저 입력해주세요.");
        }
        String content = req.content().trim();
        if (content.isEmpty()) throw new IllegalArgumentException("내용을 입력해주세요.");

        LocalDateTime since = LocalDateTime.now().minusDays(1);
        long todayCount = repo.findByDeletedAtIsNullOrderByCreatedAtDesc().stream()
                .filter(e -> user.getId().equals(e.getUserId()))
                .filter(e -> e.getCreatedAt() != null && e.getCreatedAt().isAfter(since))
                .count();
        if (todayCount >= DAILY_LIMIT) {
            throw new IllegalStateException("하루에 " + DAILY_LIMIT + "개까지 남길 수 있어요.");
        }

        GuestbookEntry saved = repo.save(GuestbookEntry.builder()
                .userId(user.getId())
                // 탈퇴 등으로 회원을 못 찾을 때 쓸 예비값. 평소 표시는 현재 닉네임을 따라간다.
                .authorName(user.displayName())
                .content(content)
                .hidden(Boolean.FALSE)
                .build());
        return GuestbookDto.from(saved, user.displayName(), user.getId());
    }

    /** 본인 글 삭제. */
    @Transactional
    public void deleteOwn(AppUser user, Long id) {
        GuestbookEntry e = find(id);
        if (!user.getId().equals(e.getUserId())) {
            throw new IllegalArgumentException("본인이 쓴 글만 지울 수 있어요.");
        }
        e.setDeletedAt(LocalDateTime.now());
        repo.save(e);
    }

    // ─── 관리자 ─────────────────────────────────────────

    public List<GuestbookDto.AdminView> listForAdmin() {
        List<GuestbookEntry> entries = repo.findByDeletedAtIsNullOrderByCreatedAtDesc();
        Map<Long, AppUser> authors = authorsOf(entries);
        return entries.stream()
                .map(e -> {
                    AppUser author = authors.get(e.getUserId());
                    return GuestbookDto.AdminView.from(
                            e,
                            displayNameOf(e, authors),
                            author == null ? null : author.getName());
                })
                .toList();
    }

    @Transactional
    public GuestbookDto.AdminView setHidden(Long id, boolean hidden) {
        GuestbookEntry e = find(id);
        e.setHidden(hidden);
        return adminViewOf(repo.save(e));
    }

    @Transactional
    public void delete(Long id) {
        GuestbookEntry e = find(id);
        e.setDeletedAt(LocalDateTime.now());
        repo.save(e);
    }

    // ─── 공통 ───────────────────────────────────────────

    private GuestbookEntry find(Long id) {
        return repo.findById(id)
                .filter(e -> e.getDeletedAt() == null)
                .orElseThrow(() -> new IllegalArgumentException("글을 찾을 수 없습니다."));
    }

    private GuestbookDto.AdminView adminViewOf(GuestbookEntry e) {
        AppUser author = userRepo.findById(e.getUserId()).orElse(null);
        return GuestbookDto.AdminView.from(
                e,
                author == null ? e.getAuthorName() : author.displayName(),
                author == null ? null : author.getName());
    }

    /** 글쓴이들을 한 번에 조회해 id → 회원 맵으로 만든다. */
    private Map<Long, AppUser> authorsOf(List<GuestbookEntry> entries) {
        List<Long> ids = entries.stream().map(GuestbookEntry::getUserId).distinct().toList();
        if (ids.isEmpty()) return Map.of();
        return userRepo.findAllById(ids).stream()
                .collect(Collectors.toMap(AppUser::getId, Function.identity(), (a, b) -> a));
    }

    /** 닉네임을 바꾸면 예전 글의 표시 이름도 함께 바뀌도록 현재 닉네임을 쓴다. */
    private static String displayNameOf(GuestbookEntry e, Map<Long, AppUser> authors) {
        AppUser author = authors.get(e.getUserId());
        return author == null ? e.getAuthorName() : author.displayName();
    }
}
