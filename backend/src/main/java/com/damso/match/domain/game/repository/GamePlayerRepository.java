package com.damso.match.domain.game.repository;

import com.damso.match.domain.game.entity.GamePlayer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GamePlayerRepository extends JpaRepository<GamePlayer, Long> {

    List<GamePlayer> findByRoomIdOrderByJoinedAtAsc(Long roomId);

    Optional<GamePlayer> findByRoomIdAndUserId(Long roomId, Long userId);

    void deleteByRoomId(Long roomId);
}
