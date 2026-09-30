package com.damso.match.domain.dating.repository;

import com.damso.match.domain.dating.entity.DatingQuestion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DatingQuestionRepository extends JpaRepository<DatingQuestion, Long> {
    List<DatingQuestion> findAllByOrderBySortOrderAsc();
}