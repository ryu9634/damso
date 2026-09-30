package com.damso.match.domain.event.repository;

import com.damso.match.domain.event.entity.Message;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findByEventIdOrderByCreatedAtDesc(Long eventId);
}
