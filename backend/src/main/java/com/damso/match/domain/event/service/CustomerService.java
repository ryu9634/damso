package com.damso.match.domain.event.service;

import com.damso.match.api.admin.event.dto.CustomerUpdateRequest;
import com.damso.match.domain.event.dto.CustomerCreateRequest;
import com.damso.match.domain.event.entity.Customer;
import com.damso.match.domain.event.entity.CustomerStatus;
import com.damso.match.domain.event.entity.SeatAssignment;
import com.damso.match.domain.event.repository.CustomerRepository;
import com.damso.match.domain.event.repository.SeatAssignmentRepository;
import com.damso.match.global.common.Gender;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final SeatAssignmentRepository seatRepository;

    /** 성별당 최대 인원 (자리 9개 × 남1·여1). */
    private static final int MAX_PER_GENDER = 9;

    @Transactional
    public Customer create(CustomerCreateRequest req) {
        return create(req, null);
    }

    /**
     * @param appUserId 손님 앱에서 로그인한 회원 id. 관리자가 직접 등록할 때는 null.
     */
    // 클래스에 걸린 readOnly 를 반드시 덮어써야 한다. 앱에서 오는 등록은 이 메서드로 바로 들어와
    // (한 개짜리 create 처럼 감싸주는 쓰기 트랜잭션이 없어서) 읽기 전용 그대로면 INSERT 가 거부된다.
    @Transactional
    public Customer create(CustomerCreateRequest req, Long appUserId) {
        long sameGender = customerRepository.countByEventIdAndGenderAndStatus(
                req.eventId(), req.gender(), CustomerStatus.ACTIVE);
        if (sameGender >= MAX_PER_GENDER) {
            String label = req.gender() == Gender.MALE ? "남성" : "여성";
            throw new IllegalStateException(
                    label + " 정원(" + MAX_PER_GENDER + "명)이 가득 찼어요. 더 등록할 수 없습니다.");
        }
        Customer c = Customer.builder()
                .eventId(req.eventId())
                .appUserId(appUserId)
                .nickname(req.nickname())
                .realName(req.realName())
                .phone(req.phone())
                .gender(req.gender())
                .mbti(req.mbti().toUpperCase())
                .valuesAnswers(req.valuesAnswers())
                .seatNumber(req.seatNumber())
                .build();
        return customerRepository.save(c);
    }

    public List<Customer> listAll(Long eventId) {
        return customerRepository.findByEventIdOrderByCreatedAtAsc(eventId);
    }

    public List<Customer> listActiveByGender(Long eventId, Gender gender) {
        return customerRepository.findByEventIdAndGenderAndStatusOrderByCreatedAtAsc(eventId, gender, CustomerStatus.ACTIVE);
    }

    public Customer get(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("손님을 찾을 수 없습니다: " + id));
    }

    @Transactional
    public Customer update(Long id, CustomerUpdateRequest req) {
        Customer c = get(id);
        c.setNickname(req.nickname());
        c.setRealName(req.realName());
        c.setPhone(req.phone());
        c.setGender(req.gender());
        c.setMbti(req.mbti().toUpperCase());
        c.setValuesAnswers(req.valuesAnswers());
        c.setSeatNumber(req.seatNumber());
        return c;
    }

    @Transactional
    public void delete(Long id) {
        customerRepository.deleteById(id);
    }

    /**
     * 입장/퇴장 상태 변경.
     * LEFT로 변경되면 모든 라운드의 좌석 배정에서 해당 손님 제거.
     */
    @Transactional
    public Customer setStatus(Long id, CustomerStatus status) {
        Customer c = get(id);
        c.setStatus(status);
        if (status == CustomerStatus.LEFT) {
            for (int round : List.of(1, 2, 3)) {
                List<SeatAssignment> mine = seatRepository.findByEventIdAndRound(c.getEventId(), round).stream()
                        .filter(s -> s.getCustomerId().equals(id))
                        .toList();
                mine.forEach(seatRepository::delete);
            }
        }
        return c;
    }

    public long countByGender(Long eventId, Gender gender) {
        return customerRepository.countByEventIdAndGender(eventId, gender);
    }}
