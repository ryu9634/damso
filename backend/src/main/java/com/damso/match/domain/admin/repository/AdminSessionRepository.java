package com.damso.match.domain.admin.repository;

import com.damso.match.domain.admin.entity.AdminSession;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface AdminSessionRepository extends JpaRepository<AdminSession, String> {
    void deleteByAdminId(Long adminId);
    void deleteByExpiresAtBefore(LocalDateTime time);
}
