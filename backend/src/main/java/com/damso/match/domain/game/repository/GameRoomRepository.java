package com.damso.match.domain.game.repository;

import com.damso.match.domain.game.entity.GameRoom;
import com.damso.match.domain.game.entity.GameRoomStatus;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GameRoomRepository extends JpaRepository<GameRoom, Long> {

    Optional<GameRoom> findByCode(String code);

    boolean existsByCode(String code);

    List<GameRoom> findByStatusNotOrderByCreatedAtDesc(GameRoomStatus status);

    List<GameRoom> findAllByOrderByCreatedAtDesc();
}
