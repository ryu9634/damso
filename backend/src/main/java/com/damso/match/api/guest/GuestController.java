package com.damso.match.api.guest;

import com.damso.match.api.guest.dto.GuestCandidateDto;
import com.damso.match.api.guest.dto.GuestMessageDto;
import com.damso.match.api.guest.dto.GuestSeatDto;
import com.damso.match.api.guest.dto.GuestSeatViewDto;
import com.damso.match.api.guest.dto.GuestSettingsDto;
import com.damso.match.domain.dating.service.MatchScoreService;
import com.damso.match.domain.event.dto.CustomerCreateRequest;
import com.damso.match.domain.event.dto.CustomerDto;
import com.damso.match.domain.event.entity.Customer;
import com.damso.match.domain.event.entity.Event;
import com.damso.match.domain.event.entity.Message;
import com.damso.match.domain.event.entity.Preference;
import com.damso.match.domain.event.entity.SeatAssignment;
import com.damso.match.domain.event.entity.ValueQuestion;
import com.damso.match.domain.event.repository.CustomerRepository;
import com.damso.match.domain.event.repository.MessageRepository;
import com.damso.match.domain.event.repository.PreferenceRepository;
import com.damso.match.domain.event.service.CustomerService;
import com.damso.match.domain.event.service.EventService;
import com.damso.match.domain.event.service.MessageService;
import com.damso.match.domain.event.service.PreferenceService;
import com.damso.match.domain.event.service.SeatAssignmentService;
import com.damso.match.domain.event.service.ValueQuestionService;
import com.damso.match.domain.member.entity.AppUser;
import com.damso.match.domain.member.service.AuthService;
import com.damso.match.domain.setting.service.FeatureService;
import com.damso.match.domain.setting.service.SettingService;
import com.damso.match.domain.setting.service.SettingsCatalog;
import com.damso.match.global.common.Gender;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 손님(모바일) 페이지에서 사용하는 API.
 *  - 입장 등록은 앱 로그인 필수(Authorization: Bearer). 등록 후 조회는 customerId 기준.
 *  - 손님 본인 정보 + 본인 자리 + 본인 호감도 + 본인 메시지 위주
 *  - 다른 손님 정보는 닉네임/MBTI/성별까지만 노출 (본명/연락처는 제외)
 *  - 관리자가 담소 소개팅을 꺼두면(환경설정 → 앱 기능 사용) 진행 관련 요청을 막는다.
 */
@RestController
@RequestMapping("/api/guest")
@RequiredArgsConstructor
public class GuestController {

    private final EventService eventService;
    private final CustomerService customerService;
    private final SeatAssignmentService seatService;
    private final PreferenceService preferenceService;
    private final MessageService messageService;
    private final ValueQuestionService questionService;
    private final MatchScoreService matchScoreService;
    private final SettingService settingService;
    private final AuthService authService;
    private final FeatureService featureService;
    private final CustomerRepository customerRepository;
    private final PreferenceRepository preferenceRepository;
    private final MessageRepository messageRepository;

    // ─── 이벤트 / 설문 ─────────────────────────────────────
    @GetMapping("/event")
    public Event currentEvent() {
        featureService.requireMatching();
        return eventService.getOrCreateCurrent();
    }

    /** 손님 화면 안내 문구 / 메시지 발송 인원 수 등 운영 설정. */
    @GetMapping("/settings")
    public com.damso.match.api.guest.dto.GuestSettingsDto settings() {
        return settingService.guestSettings();
    }

    @GetMapping("/questions")
    public List<ValueQuestion> questions() {
        return questionService.list();
    }

    // ─── 등록 / 본인 식별 ──────────────────────────────────
    /** 입장 등록 — 앱 로그인 필수. 계정당 한 이벤트에 한 번만 등록된다. */
    @PostMapping("/customers")
    public CustomerDto register(@RequestHeader(value = "Authorization", required = false) String auth,
                                @Valid @RequestBody CustomerCreateRequest req) {
        featureService.requireMatching();
        AppUser user = authService.requireUser(auth);
        Event current = eventService.getOrCreateCurrent();

        // 이미 등록했다면 새로 만들지 않고 기존 등록을 그대로 돌려준다.
        Optional<Customer> already = customerRepository.findByEventIdAndAppUserId(current.getId(), user.getId());
        if (already.isPresent()) return CustomerDto.from(already.get());

        // 손님이 지정한 eventId 무시하고 항상 현재 이벤트에 등록
        CustomerCreateRequest fixed = new CustomerCreateRequest(
                current.getId(),
                req.nickname(), req.realName(), req.phone(),
                req.gender(), req.mbti(), req.valuesAnswers(),
                req.seatNumber()
        );
        return CustomerDto.from(customerService.create(fixed, user.getId()));
    }

    /**
     * 로그인한 회원의 이번 이벤트 등록 정보.
     * 아직 등록 전이면 200 + 빈 본문(null). (앱이 등록 화면으로 보낸다)
     */
    @GetMapping("/me/account")
    public CustomerDto myRegistration(@RequestHeader(value = "Authorization", required = false) String auth) {
        AppUser user = authService.requireUser(auth);
        Event current = eventService.getOrCreateCurrent();
        return customerRepository.findByEventIdAndAppUserId(current.getId(), user.getId())
                .map(CustomerDto::from)
                .orElse(null);
    }

    @GetMapping("/me")
    public ResponseEntity<CustomerDto> me(@RequestParam Long customerId) {
        Optional<Customer> opt = customerRepository.findById(customerId);
        if (opt.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        return ResponseEntity.ok(CustomerDto.from(opt.get()));
    }

    /** 세션 끊긴 손님이 본명 + 전화번호로 재로그인. */
    @PostMapping("/login")
    public ResponseEntity<CustomerDto> login(@RequestBody LoginRequest req) {
        featureService.requireMatching();
        if (req.realName == null || req.realName.isBlank() || req.phone == null || req.phone.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        Event current = eventService.getOrCreateCurrent();
        return customerRepository
                .findByEventIdAndRealNameAndPhone(current.getId(), req.realName.trim(), req.phone.trim())
                .map(c -> ResponseEntity.ok(CustomerDto.from(c)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    public static class LoginRequest {
        public String realName;
        public String phone;
    }

    // ─── 전체 자리 배치도 ──────────────────────────────
    @GetMapping("/seats")
    public List<GuestSeatViewDto> seats(@RequestParam Long customerId, @RequestParam int round) {
        Customer me = customerService.get(customerId);
        List<SeatAssignment> all = seatService.getAssignments(me.getEventId(), round);
        Map<Long, Customer> byId = new HashMap<>();
        customerRepository.findAllById(all.stream().map(SeatAssignment::getCustomerId).toList())
                .forEach(c -> byId.put(c.getId(), c));
        return all.stream().map(s -> {
            Customer c = byId.get(s.getCustomerId());
            return new GuestSeatViewDto(
                    s.getSeatNumber(), s.getSlotIndex(), s.getGender(),
                    c == null ? null : c.getId(),
                    c == null ? "?" : c.getNickname()
            );
        }).toList();
    }

    // ─── 본인 자리 + 동석자 ────────────────────────────────
    @GetMapping("/me/seat")
    public GuestSeatDto mySeat(@RequestParam Long customerId, @RequestParam int round) {
        Customer me = customerService.get(customerId);
        List<SeatAssignment> seats = seatService.getAssignments(me.getEventId(), round);
        SeatAssignment mine = seats.stream()
                .filter(s -> s.getCustomerId().equals(customerId))
                .findFirst().orElse(null);
        if (mine == null) return GuestSeatDto.notAssigned();

        Map<Long, Customer> byId = new HashMap<>();
        customerRepository.findAllById(seats.stream().map(SeatAssignment::getCustomerId).toList())
                .forEach(c -> byId.put(c.getId(), c));

        List<GuestSeatDto.Partner> partners = seats.stream()
                .filter(s -> s.getSeatNumber() == mine.getSeatNumber() && !s.getCustomerId().equals(customerId))
                .map(s -> {
                    Customer c = byId.get(s.getCustomerId());
                    return new GuestSeatDto.Partner(c.getId(), c.getNickname(), c.getMbti(), c.getGender());
                })
                .toList();
        return new GuestSeatDto(true, mine.getSeatNumber(), partners);
    }

    /** 본인과 이성 손님들 사이 매칭 점수 (2차 자리에서 표시용). */
    @GetMapping("/me/match-scores")
    public Map<Long, Integer> myMatchScores(@RequestParam Long customerId) {
        Customer me = customerService.get(customerId);
        Gender opposite = me.getGender() == Gender.MALE ? Gender.FEMALE : Gender.MALE;
        Map<Long, Integer> result = new HashMap<>();
        for (Customer other : customerService.listActiveByGender(me.getEventId(), opposite)) {
            result.put(other.getId(), matchScoreService.totalScore(me, other));
        }
        return result;
    }

    // ─── 호감도 지목 ─────────────────────────────────────
    @GetMapping("/me/candidates")
    public List<GuestCandidateDto> candidates(@RequestParam Long customerId) {
        Customer me = customerService.get(customerId);
        Gender opposite = me.getGender() == Gender.MALE ? Gender.FEMALE : Gender.MALE;
        return customerService.listActiveByGender(me.getEventId(), opposite).stream()
                .map(c -> new GuestCandidateDto(c.getId(), c.getNickname(), c.getMbti(), c.getGender()))
                .toList();
    }

    @PostMapping("/preference")
    public Preference submitPreference(@RequestBody PreferenceRequest req) {
        featureService.requireMatching();
        Customer me = customerService.get(req.customerId);
        // 1·2·3순위는 서로 달라야 함 (null 은 미선택이므로 중복 검사에서 제외)
        java.util.List<Long> picks = java.util.stream.Stream.of(req.firstChoiceId, req.secondChoiceId, req.thirdChoiceId)
                .filter(java.util.Objects::nonNull).toList();
        if (picks.size() != picks.stream().distinct().count()) {
            throw new IllegalArgumentException("1·2·3순위는 서로 달라야 합니다.");
        }
        return preferenceService.submit(me.getEventId(), me.getId(), req.firstChoiceId, req.secondChoiceId, req.thirdChoiceId);
    }

    @GetMapping("/preference")
    public Preference myPreference(@RequestParam Long customerId) {
        Customer me = customerService.get(customerId);
        return preferenceRepository.findByEventIdOrderByCreatedAtAsc(me.getEventId()).stream()
                .filter(p -> p.getVoterId().equals(customerId))
                .findFirst().orElse(null);
    }

    // ─── 최종 메시지 ─────────────────────────────────────
    @GetMapping("/me/messages-sent")
    public List<GuestMessageDto> myMessagesSent(@RequestParam Long customerId) {
        Customer me = customerService.get(customerId);
        return enrichMessages(
                messageRepository.findByEventIdOrderByCreatedAtDesc(me.getEventId()).stream()
                        .filter(m -> m.getSenderId().equals(customerId))
                        .toList()
        );
    }

    @GetMapping("/me/messages-received")
    public List<GuestMessageDto> myMessagesReceived(@RequestParam Long customerId) {
        Customer me = customerService.get(customerId);
        return enrichMessages(
                messageRepository.findByEventIdOrderByCreatedAtDesc(me.getEventId()).stream()
                        .filter(m -> m.getReceiverId().equals(customerId))
                        .toList()
        );
    }

    private List<GuestMessageDto> enrichMessages(List<Message> messages) {
        if (messages.isEmpty()) return List.of();
        Map<Long, Customer> byId = new HashMap<>();
        messages.forEach(m -> {
            byId.computeIfAbsent(m.getSenderId(), id -> customerRepository.findById(id).orElse(null));
            byId.computeIfAbsent(m.getReceiverId(), id -> customerRepository.findById(id).orElse(null));
        });
        return messages.stream().map(m -> {
            Customer s = byId.get(m.getSenderId());
            Customer r = byId.get(m.getReceiverId());
            return new GuestMessageDto(
                    m.getId(),
                    m.getSenderId(), s == null ? "?" : s.getNickname(), s == null ? "?" : s.getMbti(),
                    m.getReceiverId(), r == null ? "?" : r.getNickname(),
                    m.getContent(), m.getSenderContact(), m.getCreatedAt()
            );
        }).toList();
    }

    @PostMapping("/message")
    public Message sendMessage(@RequestBody MessageRequest req) {
        featureService.requireMatching();
        Customer me = customerService.get(req.customerId);
        if (req.receiverId == null) throw new IllegalArgumentException("받는 사람을 선택해주세요.");
        if (req.receiverId.equals(me.getId())) throw new IllegalArgumentException("본인에게는 보낼 수 없습니다.");
        if (req.content == null || req.content.isBlank()) throw new IllegalArgumentException("메시지를 입력해주세요.");
        if (req.senderContact == null || req.senderContact.isBlank()) throw new IllegalArgumentException("본인 연락처를 입력해주세요.");
        // 본인이 이미 보낸 메시지들 (최대 2명까지 발송 가능)
        List<Message> mySent = messageRepository.findByEventIdOrderByCreatedAtDesc(me.getEventId()).stream()
                .filter(m -> m.getSenderId().equals(me.getId()))
                .toList();
        if (mySent.stream().anyMatch(m -> m.getReceiverId().equals(req.receiverId))) {
            throw new IllegalStateException("이미 이 분께 메시지를 전달했습니다.");
        }
        int maxRecipients = settingService.getInt(com.damso.match.domain.setting.service.SettingsCatalog.MAX_MESSAGE_RECIPIENTS);
        if (mySent.size() >= maxRecipients) {
            throw new IllegalStateException("메시지는 최대 " + maxRecipients + "명에게만 전달할 수 있습니다.");
        }
        return messageService.send(me.getEventId(), me.getId(), req.receiverId, req.content, req.senderContact);
    }

    public static class PreferenceRequest {
        public Long customerId;
        public Long firstChoiceId;
        public Long secondChoiceId;
        public Long thirdChoiceId;
    }

    public static class MessageRequest {
        public Long customerId;
        public Long receiverId;
        public String content;
        public String senderContact;
    }
}
