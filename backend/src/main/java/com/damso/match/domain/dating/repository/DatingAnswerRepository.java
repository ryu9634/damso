package com.damso.match.domain.dating.repository;

import com.damso.match.domain.dating.entity.DatingAnswer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface DatingAnswerRepository extends JpaRepository<DatingAnswer, Long> {
    List<DatingAnswer> findByProfileId(Long profileId);

    @Transactional
    void deleteByProfileId(Long profileId);

    @Transactional
    void deleteByQuestionId(Long questionId);
}