package com.damso.match.domain.event.repository;

import com.damso.match.domain.event.entity.Customer;
import com.damso.match.domain.event.entity.CustomerStatus;
import com.damso.match.global.common.Gender;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    List<Customer> findByEventIdOrderByCreatedAtAsc(Long eventId);
    List<Customer> findByEventIdAndGenderOrderByCreatedAtAsc(Long eventId, Gender gender);
    List<Customer> findByEventIdAndStatusOrderByCreatedAtAsc(Long eventId, CustomerStatus status);
    List<Customer> findByEventIdAndGenderAndStatusOrderByCreatedAtAsc(Long eventId, Gender gender, CustomerStatus status);
    Optional<Customer> findByEventIdAndRealNameAndPhone(Long eventId, String realName, String phone);

    /** 로그인한 앱 회원이 이번 이벤트에 이미 등록했는지. */
    Optional<Customer> findByEventIdAndAppUserId(Long eventId, Long appUserId);
    long countByEventId(Long eventId);
    long countByEventIdAndGender(Long eventId, Gender gender);
    long countByEventIdAndStatus(Long eventId, CustomerStatus status);
    long countByEventIdAndGenderAndStatus(Long eventId, Gender gender, CustomerStatus status);
}
