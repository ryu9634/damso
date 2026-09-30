package com.damso.match.domain.member.repository;

import com.damso.match.domain.member.entity.AppUser;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    /** 아이디 로그인 — 탈퇴 회원 제외. */
    Optional<AppUser> findByLoginIdAndDeletedAtIsNull(String loginId);

    /** 아이디 중복 확인 — 탈퇴 계정이 쓰던 아이디도 재사용하지 않는다. */
    boolean existsByLoginId(String loginId);

    /** 닉네임 중복 확인. (공개 화면에서 서로 구분되도록 유일하게 둔다) */
    boolean existsByNickname(String nickname);

    /**
     * 휴대폰번호로 회원 찾기 — 탈퇴 회원 제외.
     * 한 사람이 아이디로도, 카카오로도 계정을 새로 만드는 것을 막는 기준값이다.
     * 탈퇴 계정을 제외하는 것은 의도된 것이다. 번호를 영영 묶어 두면 재가입을 막게 된다.
     *
     * 하나만 나오는 것이 정상이지만 List 로 받는다. DB 제약을 걸기 전에 이미 들어온 중복이
     * 남아 있을 수 있고, Optional 로 받으면 그 경우 조회 자체가 예외로 터진다.
     */
    List<AppUser> findByPhoneAndDeletedAtIsNull(String phone);

    List<AppUser> findByDeletedAtIsNullOrderByCreatedAtDesc();
}
