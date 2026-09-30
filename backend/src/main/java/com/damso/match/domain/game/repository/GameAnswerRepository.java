package com.damso.match.domain.game.repository;

import com.damso.match.domain.game.entity.GameAnswer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GameAnswerRepository extends JpaRepository<GameAnswer, Long> {

    List<GameAnswer> findByRoundId(Long roundId);

    Optional<GameAnswer> findByRoundIdAndUserId(Long roundId, Long userId);
}
