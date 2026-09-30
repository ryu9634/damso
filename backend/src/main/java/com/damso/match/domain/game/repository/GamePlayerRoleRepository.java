package com.damso.match.domain.game.repository;

import com.damso.match.domain.game.entity.GamePlayerRole;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GamePlayerRoleRepository extends JpaRepository<GamePlayerRole, Long> {

    List<GamePlayerRole> findByRoundId(Long roundId);

    Optional<GamePlayerRole> findByRoundIdAndUserId(Long roundId, Long userId);
}
