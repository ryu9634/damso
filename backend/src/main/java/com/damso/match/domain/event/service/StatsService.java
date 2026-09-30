package com.damso.match.domain.event.service;

import com.damso.match.api.admin.event.dto.StatsDto;
import com.damso.match.domain.event.entity.Customer;
import com.damso.match.domain.event.entity.CustomerStatus;
import com.damso.match.domain.event.entity.Event;
import com.damso.match.domain.event.entity.Message;
import com.damso.match.domain.event.entity.Preference;
import com.damso.match.domain.event.repository.CustomerRepository;
import com.damso.match.domain.event.repository.EventRepository;
import com.damso.match.domain.event.repository.MessageRepository;
import com.damso.match.domain.event.repository.PreferenceRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 기간 내(이벤트 생성일 기준) 여러 이벤트를 합산한 통계.
 *  - MBTI I/E 비율
 *  - 호감도 지목 매칭률 (쌍방 매칭된 인원 / 지목 참여 인원)
 *  - 메시지 전송률 (보낸 인원 / 전체 인원)
 *  - 메시지 쌍방 전송률 (서로 보낸 인원 / 보낸 인원)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatsService {

    private final EventRepository eventRepository;
    private final CustomerRepository customerRepository;
    private final PreferenceRepository preferenceRepository;
    private final MessageRepository messageRepository;

    public StatsDto summarize(LocalDate from, LocalDate to) {
        LocalDateTime fromDt = (from == null) ? LocalDateTime.of(1970, 1, 1, 0, 0) : from.atStartOfDay();
        // to 는 해당 날짜 포함 → 다음날 0시 직전까지
        LocalDateTime toDt = (to == null) ? LocalDateTime.now().plusYears(100) : to.plusDays(1).atStartOfDay();

        List<Event> events = eventRepository.findByCreatedAtBetweenOrderByIdAsc(fromDt, toDt);

        long totalCustomers = 0, mbtiI = 0, mbtiE = 0;
        long prefParticipants = 0, prefMatched = 0;
        long msgSenders = 0, mutualMsgPeople = 0;

        for (Event e : events) {
            Long eid = e.getId();

            // ─── 손님 / MBTI ───────────────────────────────
            List<Customer> customers = customerRepository
                    .findByEventIdAndStatusOrderByCreatedAtAsc(eid, CustomerStatus.ACTIVE);
            totalCustomers += customers.size();
            for (Customer c : customers) {
                String mbti = c.getMbti();
                if (mbti == null || mbti.isBlank()) continue;
                char head = Character.toUpperCase(mbti.charAt(0));
                if (head == 'I') mbtiI++;
                else if (head == 'E') mbtiE++;
            }

            // ─── 호감도 지목 매칭률 ─────────────────────────
            List<Preference> prefs = preferenceRepository.findByEventIdOrderByCreatedAtAsc(eid);
            Map<Long, Set<Long>> designations = new HashMap<>();
            for (Preference p : prefs) {
                Set<Long> targets = new HashSet<>();
                if (p.getFirstChoiceId() != null) targets.add(p.getFirstChoiceId());
                if (p.getSecondChoiceId() != null) targets.add(p.getSecondChoiceId());
                if (p.getThirdChoiceId() != null) targets.add(p.getThirdChoiceId());
                designations.put(p.getVoterId(), targets);
            }
            prefParticipants += designations.size();
            Set<Long> matched = new HashSet<>();
            for (Map.Entry<Long, Set<Long>> entry : designations.entrySet()) {
                Long a = entry.getKey();
                for (Long b : entry.getValue()) {
                    if (designations.getOrDefault(b, Set.of()).contains(a)) {
                        matched.add(a);
                        matched.add(b);
                    }
                }
            }
            prefMatched += matched.size();

            // ─── 메시지 전송률 / 쌍방 전송률 ────────────────
            List<Message> msgs = messageRepository.findByEventIdOrderByCreatedAtDesc(eid);
            Set<Long> senders = msgs.stream().map(Message::getSenderId).collect(Collectors.toSet());
            msgSenders += senders.size();

            Set<String> directed = msgs.stream()
                    .map(m -> m.getSenderId() + "->" + m.getReceiverId())
                    .collect(Collectors.toSet());
            Set<Long> mutual = new HashSet<>();
            for (Message m : msgs) {
                if (directed.contains(m.getReceiverId() + "->" + m.getSenderId())) {
                    mutual.add(m.getSenderId());
                    mutual.add(m.getReceiverId());
                }
            }
            mutualMsgPeople += mutual.size();
        }

        return new StatsDto(
                from, to, events.size(),
                totalCustomers,
                mbtiI, mbtiE,
                prefParticipants, prefMatched,
                msgSenders, mutualMsgPeople
        );
    }
}
