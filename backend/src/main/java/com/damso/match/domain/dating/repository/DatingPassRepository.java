package com.damso.match.domain.dating.repository;

import com.damso.match.domain.dating.entity.DatingPass;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DatingPassRepository extends JpaRepository<DatingPass, Long> {
    List<DatingPass> findByFromProfileId(Long fromProfileId);
    Optional<DatingPass> findByFromProfileIdAndToProfileId(Long fromProfileId, Long toProfileId);
    void deleteByFromProfileId(Long fromProfileId);
    void deleteByFromProfileIdOrToProfileId(Long fromProfileId, Long toProfileId);
}
