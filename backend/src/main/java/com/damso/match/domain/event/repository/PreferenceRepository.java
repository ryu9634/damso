package com.damso.match.domain.event.repository;

import com.damso.match.domain.event.entity.Preference;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PreferenceRepository extends JpaRepository<Preference, Long> {
    List<Preference> findByEventIdOrderByCreatedAtAsc(Long eventId);
}
