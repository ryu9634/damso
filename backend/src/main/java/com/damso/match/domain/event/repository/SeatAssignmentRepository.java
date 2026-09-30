package com.damso.match.domain.event.repository;

import com.damso.match.domain.event.entity.SeatAssignment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SeatAssignmentRepository extends JpaRepository<SeatAssignment, Long> {
    List<SeatAssignment> findByEventIdAndRoundOrderBySeatNumberAsc(Long eventId, Integer round);
    List<SeatAssignment> findByEventIdAndRound(Long eventId, Integer round);
    void deleteByEventIdAndRound(Long eventId, Integer round);
}
