package com.damso.match.domain.dating.repository;

import com.damso.match.domain.dating.entity.DatingMatch;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DatingMatchRepository extends JpaRepository<DatingMatch, Long> {
    Optional<DatingMatch> findByProfileAIdAndProfileBId(Long profileAId, Long profileBId);
    void deleteByProfileAIdOrProfileBId(Long profileAId, Long profileBId);
}