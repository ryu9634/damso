package com.damso.match.api.admin.event;

import com.damso.match.api.admin.event.dto.SeatAssignmentDto;
import com.damso.match.domain.event.entity.Customer;
import com.damso.match.domain.event.entity.SeatAssignment;
import com.damso.match.domain.event.repository.CustomerRepository;
import com.damso.match.domain.event.service.SeatAssignmentService;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/seats")
@RequiredArgsConstructor
public class AdminSeatController {

    private final SeatAssignmentService seatService;
    private final CustomerRepository customerRepository;

    @GetMapping
    public List<SeatAssignmentDto> list(@RequestParam Long eventId, @RequestParam int round) {
        List<SeatAssignment> seats = seatService.getAssignments(eventId, round);
        Map<Long, Customer> byId = new HashMap<>();
        customerRepository.findByEventIdOrderByCreatedAtAsc(eventId)
                .forEach(c -> byId.put(c.getId(), c));
        return seats.stream().map(s -> {
            Customer c = byId.get(s.getCustomerId());
            return SeatAssignmentDto.of(s,
                    c == null ? "?" : c.getNickname(),
                    c == null ? "?" : c.getMbti());
        }).toList();
    }

    @PostMapping("/round1/auto")
    public List<SeatAssignmentDto> assignRound1(@RequestParam Long eventId) {
        seatService.assignRound1(eventId);
        return list(eventId, 1);
    }

    @PostMapping("/round2/auto")
    public List<SeatAssignmentDto> assignRound2(@RequestParam Long eventId) {
        seatService.assignRound2(eventId);
        return list(eventId, 2);
    }

    @PostMapping("/round3/auto")
    public List<SeatAssignmentDto> assignRound3(@RequestParam Long eventId) {
        seatService.assignRound3(eventId);
        return list(eventId, 3);
    }

    @PostMapping("/round4/auto")
    public List<SeatAssignmentDto> assignRound4(@RequestParam Long eventId) {
        seatService.assignRound4(eventId);
        return list(eventId, 4);
    }

    @PutMapping("/manual")
    public SeatAssignmentDto manual(@RequestBody ManualAssignRequest req) {
        SeatAssignment s = seatService.manualAssign(req.eventId, req.round, req.seatNumber, req.slotIndex, req.customerId);
        Customer c = customerRepository.findById(req.customerId).orElse(null);
        return SeatAssignmentDto.of(s,
                c == null ? "?" : c.getNickname(),
                c == null ? "?" : c.getMbti());
    }

    @DeleteMapping
    public void remove(@RequestParam Long eventId, @RequestParam int round, @RequestParam Long customerId) {
        seatService.removeAssignment(eventId, round, customerId);
    }

    @DeleteMapping("/round")
    public void clearRound(@RequestParam Long eventId, @RequestParam int round) {
        seatService.clearRound(eventId, round);
    }

    public static class ManualAssignRequest {
        public Long eventId;
        public Integer round;
        public Integer seatNumber;
        public Integer slotIndex;
        public Long customerId;
    }
}
