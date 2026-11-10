package com.damso.match.domain.dating.repository;

import com.damso.match.domain.dating.entity.DatingMatch;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DatingMatchRepository extends JpaRepository<DatingMatch, Long> {
    void deleteByProfileAIdOrProfileBId(Long profileAId, Long profileBId);
}