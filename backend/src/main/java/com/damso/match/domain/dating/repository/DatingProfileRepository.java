package com.damso.match.domain.dating.repository;

import com.damso.match.domain.dating.entity.DatingProfile;
import com.damso.match.global.common.Gender;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DatingProfileRepository extends JpaRepository<DatingProfile, Long> {
    /** 손님 열람용 — 소프트 삭제된 프로필 제외. */
    List<DatingProfile> findByGenderAndDeletedAtIsNullOrderByCreatedAtDesc(Gender gender);

    /** 로그인용 — 소프트 삭제된 프로필 제외. */
    Optional<DatingProfile> findByNameAndContactAndDeletedAtIsNull(String name, String contact);

    /** 앱 계정에 연결된 프로필. */
    Optional<DatingProfile> findByAppUserIdAndDeletedAtIsNull(Long appUserId);

    List<DatingProfile> findByDeletedAtIsNull();
}