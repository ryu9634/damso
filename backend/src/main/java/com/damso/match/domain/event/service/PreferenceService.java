package com.damso.match.domain.event.service;

import com.damso.match.api.admin.event.dto.PreferenceDto;
import com.damso.match.domain.event.entity.Preference;
import com.damso.match.domain.event.repository.CustomerRepository;
import com.damso.match.domain.event.repository.PreferenceRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PreferenceService {

    private final PreferenceRepository preferenceRepository;
    private final CustomerRepository customerRepository;

    @Transactional
    public Preference submit(Long eventId, Long voterId, Long firstChoiceId, Long secondChoiceId, Long thirdChoiceId) {
        // 기존 지목 있으면 업데이트, 없으면 생성
        Preference existing = preferenceRepository.findByEventIdOrderByCreatedAtAsc(eventId).stream()
                .filter(p -> p.getVoterId().equals(voterId))
                .findFirst()
                .orElse(null);
        if (existing != null) {
            existing.setFirstChoiceId(firstChoiceId);
            existing.setSecondChoiceId(secondChoiceId);
            existing.setThirdChoiceId(thirdChoiceId);
            return existing;
        }
        return preferenceRepository.save(Preference.builder()
                .eventId(eventId).voterId(voterId)
                .firstChoiceId(firstChoiceId).secondChoiceId(secondChoiceId).thirdChoiceId(thirdChoiceId)
                .build());
    }

    public List<PreferenceDto> listWithNicknames(Long eventId) {
        List<Preference> prefs = preferenceRepository.findByEventIdOrderByCreatedAtAsc(eventId);
        Map<Long, String> nicknames = new HashMap<>();
        customerRepository.findByEventIdOrderByCreatedAtAsc(eventId)
                .forEach(c -> nicknames.put(c.getId(), c.getNickname()));

        return prefs.stream().map(p -> new PreferenceDto(
                p.getId(),
                p.getVoterId(), nicknames.getOrDefault(p.getVoterId(), "?"),
                p.getFirstChoiceId(), nicknames.getOrDefault(p.getFirstChoiceId(), "?"),
                p.getSecondChoiceId(), p.getSecondChoiceId() == null ? null : nicknames.getOrDefault(p.getSecondChoiceId(), "?"),
                p.getThirdChoiceId(), p.getThirdChoiceId() == null ? null : nicknames.getOrDefault(p.getThirdChoiceId(), "?")
        )).toList();
    }

    public long count(Long eventId) {
        return preferenceRepository.findByEventIdOrderByCreatedAtAsc(eventId).size();
    }
}
