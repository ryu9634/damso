package com.damso.match.domain.admin.repository;

import com.damso.match.domain.admin.entity.AdminUser;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AdminUserRepository extends JpaRepository<AdminUser, Long> {
    Optional<AdminUser> findByUsername(String username);
}
