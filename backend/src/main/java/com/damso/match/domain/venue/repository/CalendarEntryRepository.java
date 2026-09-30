package com.damso.match.domain.venue.repository;

import com.damso.match.domain.venue.entity.CalendarEntry;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface CalendarEntryRepository extends JpaRepository<CalendarEntry, Long> {

    List<CalendarEntry> findAllByOrderByStartDayAscIdAsc();

    /**
     * 기간에 걸쳐 있는 일정. 며칠짜리 일정도 하루라도 겹치면 나온다.
     * (endDay 가 비어 있으면 하루짜리라 startDay 로 본다)
     */
    @Query("""
            SELECT c FROM CalendarEntry c
            WHERE c.startDay <= :to
              AND COALESCE(c.endDay, c.startDay) >= :from
            ORDER BY c.startDay ASC, c.id ASC
            """)
    List<CalendarEntry> findOverlapping(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
