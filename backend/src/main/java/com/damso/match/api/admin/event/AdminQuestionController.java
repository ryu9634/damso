package com.damso.match.api.admin.event;

import com.damso.match.domain.event.entity.ValueQuestion;
import com.damso.match.domain.event.service.ValueQuestionService;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/questions")
@RequiredArgsConstructor
public class AdminQuestionController {

    private final ValueQuestionService service;

    @GetMapping
    public List<ValueQuestion> list() {
        return service.list();
    }

    @PostMapping
    public ValueQuestion create(@RequestBody QuestionRequest req) {
        return service.create(req.label, req.optionA, req.optionB);
    }

    @PutMapping("/{id}")
    public ValueQuestion update(@PathVariable Long id, @RequestBody QuestionRequest req) {
        return service.update(id, req.label, req.optionA, req.optionB);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @PostMapping("/{id}/move")
    public List<ValueQuestion> move(@PathVariable Long id, @RequestParam String direction) {
        return service.move(id, "up".equalsIgnoreCase(direction));
    }

    @PostMapping("/reset-default")
    public List<ValueQuestion> resetDefault() {
        return service.resetToDefault();
    }

    public static class QuestionRequest {
        public String label;
        public String optionA;
        public String optionB;
    }
}
