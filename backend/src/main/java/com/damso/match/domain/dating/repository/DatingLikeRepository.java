package com.damso.match.domain.dating.repository;

import com.damso.match.domain.dating.entity.DatingLike;
import com.damso.match.domain.dating.entity.DatingLikeStatus;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DatingLikeRepository extends JpaRepository<DatingLike, Long> {
    List<DatingLike> findByFromProfileId(Long fromProfileId);
    List<DatingLike> findByToProfileId(Long toProfileId);
    Optional<DatingLike> findByFromProfileIdAndToProfileId(Long fromProfileId, Long toProfileId);
    List<DatingLike> findByStatus(DatingLikeStatus status);
    void deleteByFromProfileIdOrToProfileId(Long fromProfileId, Long toProfileId);
}
