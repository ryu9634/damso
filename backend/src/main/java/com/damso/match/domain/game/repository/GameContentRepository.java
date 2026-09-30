package com.damso.match.domain.game.repository;

import com.damso.match.domain.game.entity.GameContent;
import com.damso.match.domain.game.entity.GameType;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GameContentRepository extends JpaRepository<GameContent, Long> {

    List<GameContent> findAllByOrderByTypeAscSortOrderAscIdAsc();

    List<GameContent> findByType(GameType type);

    long countByType(GameType type);
}
