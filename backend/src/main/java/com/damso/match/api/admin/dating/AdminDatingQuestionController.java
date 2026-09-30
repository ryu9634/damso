package com.damso.match.api.admin.dating;

import com.damso.match.domain.dating.dto.DatingQuestionDto;
import com.damso.match.domain.dating.service.DatingQuestionService;
import com.damso.match.domain.dating.entity.DatingQuestion.QType;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 관리자: 소개팅 상세 질문(커스텀) 관리.
 *  - 추가/수정/삭제/정렬. 손님 가입 화면이 이 목록을 따라 동적으로 그려진다.
 */
@RestController
@RequestMapping("/api/admin/dating/questions")
@RequiredArgsConstructor
public class AdminDatingQuestionController {

    private final DatingQuestionService service;

    @GetMapping
    public List<DatingQuestionDto> list() {
        return service.list().stream().map(DatingQuestionDto::from).toList();
    }

    @PostMapping
    public DatingQuestionDto create(@RequestBody QuestionRequest req) {
        return DatingQuestionDto.from(
                service.create(req.label, parseType(req.type), req.options, req.required));
    }

    @PutMapping("/{id}")
    public DatingQuestionDto update(@PathVariable Long id, @RequestBody QuestionRequest req) {
        return DatingQuestionDto.from(
                service.update(id, req.label, parseType(req.type), req.options, req.required));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @PostMapping("/{id}/move")
    public List<DatingQuestionDto> move(@PathVariable Long id, @RequestParam String direction) {
        return service.move(id, "up".equalsIgnoreCase(direction))
                .stream().map(DatingQuestionDto::from).toList();
    }

    private static QType parseType(String t) {
        if (t == null) return QType.TEXT;
        try {
            return QType.valueOf(t.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("알 수 없는 질문 유형입니다: " + t);
        }
    }

    public static class QuestionRequest {
        public String label;
        public String type;     // TEXT | TEXTAREA | NUMBER | SELECT
        public String options;  // SELECT 일 때 줄바꿈 구분 선택지
        public boolean required;
    }
}