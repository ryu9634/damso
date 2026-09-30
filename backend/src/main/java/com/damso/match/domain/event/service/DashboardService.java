package com.damso.match.domain.event.service;

import com.damso.match.api.admin.event.dto.DashboardDto;
import com.damso.match.domain.event.entity.Event;
import com.damso.match.domain.event.repository.SeatAssignmentRepository;
import com.damso.match.global.common.Gender;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final EventService eventService;
    private final CustomerService customerService;
    private final SeatAssignmentRepository seatRepo;
    private final PreferenceService preferenceService;
    private final MessageService messageService;

    public DashboardDto summarize(Long eventId) {
        Event e = eventService.get(eventId);
        long male = customerService.countByGender(eventId, Gender.MALE);
        long female = customerService.countByGender(eventId, Gender.FEMALE);
        long total = male + female;

        long r1 = seatRepo.findByEventIdAndRound(eventId, 1).size();
        long r2 = seatRepo.findByEventIdAndRound(eventId, 2).size();
        long r3 = seatRepo.findByEventIdAndRound(eventId, 3).size();
        long r4 = seatRepo.findByEventIdAndRound(eventId, 4).size();

        return new DashboardDto(
                e.getId(), e.getName(), e.getStatus(),
                total, male, female,
                r1, r2, r3, r4,
                preferenceService.count(eventId),
                messageService.count(eventId)
        );
    }
}
