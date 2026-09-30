package com.damso.match.domain.notification.repository;

import com.damso.match.domain.notification.entity.PushSubscription;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {
    List<PushSubscription> findByAppUserId(Long appUserId);
    Optional<PushSubscription> findByEndpoint(String endpoint);
    void deleteByEndpoint(String endpoint);
    void deleteByAppUserId(Long appUserId);
    int countByAppUserId(Long appUserId);
}
