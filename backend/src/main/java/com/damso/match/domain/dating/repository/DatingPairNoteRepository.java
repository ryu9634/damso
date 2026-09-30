package com.damso.match.domain.dating.repository;

import com.damso.match.domain.dating.entity.DatingPairNote;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DatingPairNoteRepository extends JpaRepository<DatingPairNote, Long> {
    Optional<DatingPairNote> findByProfileAIdAndProfileBId(Long profileAId, Long profileBId);

    /** 이 회원이 낀 페어 전부 (약속 조회용). */
    List<DatingPairNote> findByProfileAIdOrProfileBId(Long profileAId, Long profileBId);
}
