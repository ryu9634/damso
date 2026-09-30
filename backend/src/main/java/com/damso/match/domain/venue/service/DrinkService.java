package com.damso.match.domain.venue.service;

import com.damso.match.api.admin.venue.dto.DrinkItemDto;
import com.damso.match.domain.venue.entity.DrinkItem;
import com.damso.match.domain.venue.repository.DrinkItemRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 앱 홈에 보여줄 술 · 칵테일 메뉴.
 *  - 관리자가 이모지/사진과 함께 등록하고 순서를 정한다.
 *  - '오늘의 추천'은 한 잔만 켜지도록 저장할 때 나머지를 꺼준다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DrinkService {

    private final DrinkItemRepository repo;

    /** 관리자 목록 — 숨긴 항목도 포함. */
    public List<DrinkItemDto> listAll() {
        return repo.findAllByOrderBySortOrderAscIdAsc().stream().map(DrinkItemDto::from).toList();
    }

    /** 앱 홈용 — 노출 중인 항목만. */
    public List<DrinkItem> listVisible() {
        return repo.findAllByOrderBySortOrderAscIdAsc().stream()
                .filter(DrinkItem::isVisibleItem)
                .toList();
    }

    /** 오늘의 추천 한 잔. 없으면 null. */
    public DrinkItem recommended() {
        return listVisible().stream()
                .filter(DrinkItem::isRecommendedItem)
                .findFirst()
                .orElse(null);
    }

    @Transactional
    public DrinkItemDto create(DrinkItemDto.SaveRequest req) {
        DrinkItem d = DrinkItem.builder()
                .sortOrder(req.sortOrder() == null ? nextSortOrder() : req.sortOrder())
                .build();
        apply(d, req);
        repo.save(d);
        if (d.isRecommendedItem()) keepSingleRecommended(d.getId());
        return DrinkItemDto.from(d);
    }

    @Transactional
    public DrinkItemDto update(Long id, DrinkItemDto.SaveRequest req) {
        DrinkItem d = get(id);
        if (req.sortOrder() != null) d.setSortOrder(req.sortOrder());
        apply(d, req);
        repo.save(d);
        if (d.isRecommendedItem()) keepSingleRecommended(d.getId());
        return DrinkItemDto.from(d);
    }

    @Transactional
    public void delete(Long id) {
        repo.delete(get(id));
    }

    /** 화면에서 위/아래로 옮길 때 쓰는 순서 일괄 저장. */
    @Transactional
    public void reorder(List<Long> idsInOrder) {
        if (idsInOrder == null) return;
        int order = 0;
        for (Long id : idsInOrder) {
            DrinkItem d = repo.findById(id).orElse(null);
            if (d == null) continue;
            d.setSortOrder(order++);
            d.setUpdatedAt(LocalDateTime.now());
            repo.save(d);
        }
    }

    /** 첫 실행 때 홈이 비지 않도록 기본 메뉴를 넣어둔다. (한 번이라도 등록했으면 건드리지 않음) */
    @Transactional
    public void seedDefaultsIfEmpty() {
        if (repo.count() > 0) return;
        record Seed(String emoji, String name, String desc, boolean recommended) {}
        List<Seed> seeds = List.of(
                new Seed("🍸", "담소 마티니", "드라이하게, 올리브 두 알.", false),
                new Seed("🍹", "오늘의 하이볼", "주인장이 그날 기분으로 고르는 한 잔.", true),
                new Seed("🍷", "글라스 와인", "레드·화이트 중에 골라보세요.", false),
                new Seed("🍺", "생맥주", "시원하게 한 잔.", false),
                new Seed("🥃", "위스키", "천천히 마시기 좋은 한 잔.", false),
                new Seed("🍶", "전통주", "담소가 골라둔 우리 술.", false)
        );
        int order = 0;
        for (Seed s : seeds) {
            repo.save(DrinkItem.builder()
                    .emoji(s.emoji()).name(s.name()).description(s.desc())
                    .sortOrder(order++).visible(Boolean.TRUE)
                    .recommended(s.recommended())
                    .build());
        }
    }

    public DrinkItem get(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("메뉴를 찾을 수 없습니다."));
    }

    private void apply(DrinkItem d, DrinkItemDto.SaveRequest req) {
        d.setEmoji(emptyToNull(req.emoji()));
        d.setName(req.name().trim());
        d.setDescription(emptyToNull(req.description()));
        d.setImageUrl(emptyToNull(req.imageUrl()));
        d.setVisible(!Boolean.FALSE.equals(req.visible()));
        d.setRecommended(Boolean.TRUE.equals(req.recommended()));
        d.setUpdatedAt(LocalDateTime.now());
    }

    /** 추천은 한 잔만. 방금 켠 항목을 빼고 나머지를 끈다. */
    private void keepSingleRecommended(Long keepId) {
        for (DrinkItem other : repo.findAll()) {
            if (other.getId().equals(keepId) || !other.isRecommendedItem()) continue;
            other.setRecommended(Boolean.FALSE);
            repo.save(other);
        }
    }

    private int nextSortOrder() {
        return repo.findAllByOrderBySortOrderAscIdAsc().stream()
                .map(d -> d.getSortOrder() == null ? 0 : d.getSortOrder())
                .max(Integer::compareTo)
                .map(n -> n + 1)
                .orElse(0);
    }

    private static String emptyToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
