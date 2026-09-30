package com.damso.match.domain.member.repository;

import com.damso.match.domain.member.entity.UserSession;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface UserSessionRepository extends JpaRepository<UserSession, String> {

    void deleteByUserId(Long userId);

    /** 만료된 세션 정리 (로그인할 때마다 가볍게 호출). */
    void deleteByExpiresAtBefore(LocalDateTime time);
}