package com.damso.match.domain.game.repository;

import com.damso.match.domain.game.entity.GameRound;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GameRoundRepository extends JpaRepository<GameRound, Long> {

    Optional<GameRound> findFirstByRoomIdOrderByIdDesc(Long roomId);

    List<GameRound> findByRoomIdOrderByIdDesc(Long roomId);

    void deleteByRoomId(Long roomId);
}
