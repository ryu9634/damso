package com.damso.match.domain.event.service;

import com.damso.match.domain.event.entity.Event;
import com.damso.match.domain.event.entity.EventStatus;
import com.damso.match.domain.event.repository.CustomerRepository;
import com.damso.match.domain.event.repository.EventRepository;
import com.damso.match.domain.event.repository.MessageRepository;
import com.damso.match.domain.event.repository.PreferenceRepository;
import com.damso.match.domain.event.repository.SeatAssignmentRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventService {

    private final EventRepository eventRepository;
    private final CustomerRepository customerRepository;
    private final SeatAssignmentRepository seatRepository;
    private final PreferenceRepository preferenceRepository;
    private final MessageRepository messageRepository;

    @Transactional
    public Event getOrCreateCurrent() {
        return eventRepository.findTopByOrderByIdDesc()
                .orElseGet(() -> {
                    Event e = Event.builder()
                            .name("담소혼술바 매칭 이벤트")
                            .status(EventStatus.REGISTRATION)
                            .build();
                    return eventRepository.save(e);
                });
    }

    public Event get(Long id) {
        return eventRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("이벤트를 찾을 수 없습니다: " + id));
    }

    public List<Event> listAll() {
        return eventRepository.findAllByOrderByIdDesc();
    }

    @Transactional
    public Event updateStatus(Long id, EventStatus status) {
        Event e = get(id);
        e.setStatus(status);
        return e;
    }

    /** 새 이벤트 시작: 기존 이벤트는 CLOSED로 마감, 새 이벤트 생성. */
    @Transactional
    public Event startNew(String name) {
        eventRepository.findTopByOrderByIdDesc().ifPresent(prev -> {
            prev.setStatus(EventStatus.CLOSED);
        });
        return eventRepository.save(Event.builder()
                .name(name == null || name.isBlank() ? "담소혼술바 매칭 이벤트" : name)
                .status(EventStatus.REGISTRATION)
                .build());
    }

    /** 현재 이벤트의 모든 손님/좌석/지목/메시지 데이터 삭제 (이벤트 자체는 유지). */
    @Transactional
    public void clearEventData(Long eventId) {
        List.of(1, 2, 3).forEach(r -> seatRepository.deleteByEventIdAndRound(eventId, r));
        preferenceRepository.findByEventIdOrderByCreatedAtAsc(eventId)
                .forEach(preferenceRepository::delete);
        messageRepository.findByEventIdOrderByCreatedAtDesc(eventId)
                .forEach(messageRepository::delete);
        customerRepository.findByEventIdOrderByCreatedAtAsc(eventId)
                .forEach(customerRepository::delete);
        Event e = get(eventId);
        e.setStatus(EventStatus.REGISTRATION);
    }
}
