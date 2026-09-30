package com.damso.match.domain.venue.repository;

import com.damso.match.domain.venue.entity.DrinkItem;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DrinkItemRepository extends JpaRepository<DrinkItem, Long> {

    /** 노출 순서 → 등록 순. 관리자 목록과 홈이 같은 순서를 쓴다. */
    List<DrinkItem> findAllByOrderBySortOrderAscIdAsc();
}
