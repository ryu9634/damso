package com.damso.match.domain.member.repository;

import com.damso.match.domain.member.entity.AuthProvider;
import com.damso.match.domain.member.entity.UserSocialLink;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserSocialLinkRepository extends JpaRepository<UserSocialLink, Long> {

    Optional<UserSocialLink> findByProviderAndProviderId(AuthProvider provider, String providerId);

    List<UserSocialLink> findByUserId(Long userId);

    Optional<UserSocialLink> findByUserIdAndProvider(Long userId, AuthProvider provider);

    void deleteByUserId(Long userId);
}
