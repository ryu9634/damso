package com.damso.match.domain.event.repository;

import com.damso.match.domain.event.entity.Event;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Long> {
    Optional<Event> findTopByOrderByIdDesc();
    List<Event> findAllByOrderByIdDesc();
    List<Event> findByCreatedAtBetweenOrderByIdAsc(LocalDateTime from, LocalDateTime to);
}
