package com.damso.match.domain.event.repository;

import com.damso.match.domain.event.entity.ValueQuestion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ValueQuestionRepository extends JpaRepository<ValueQuestion, Long> {
    List<ValueQuestion> findAllByOrderBySortOrderAsc();
}
