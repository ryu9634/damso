package com.damso.match.domain.member.service;

import com.damso.match.domain.member.dto.AuthRequests;
import com.damso.match.domain.member.entity.AppUser;
import com.damso.match.domain.member.entity.AuthProvider;
import com.damso.match.domain.member.entity.UserSession;
import com.damso.match.domain.member.entity.UserSocialLink;
import com.damso.match.domain.member.repository.AppUserRepository;
import com.damso.match.domain.member.repository.UserSessionRepository;
import com.damso.match.domain.member.repository.UserSocialLinkRepository;
import com.damso.match.domain.notification.repository.AppNotificationRepository;
import com.damso.match.global.config.DataSeeder;
import com.damso.match.global.error.GlobalExceptionHandler;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 앱 회원 가입/로그인/세션.
 *  - 기본 로그인은 아이디 + 비밀번호(BCrypt). 성명·휴대폰번호는 로그인 수단으로 쓰지 않는다.
 *  - 간편로그인은 {@link UserSocialLink} 로 계정에 붙인다. 아이디 계정과 소셜 계정이 한 사람이면
 *    마이페이지에서 연결해 하나로 합칠 수 있고, (provider, providerId) 유일 제약으로 중복 연결을 막는다.
 *  - 세션은 불투명 토큰(UserSession)이며 Authorization: Bearer 로 전달받는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    /** 세션 유효기간(일). */
    private static final int SESSION_DAYS = 90;

    /**
     * 관리자가 초기화할 때 넣어 주는 임시 비밀번호.
     * 매장에서 말로 알려주는 일이 많아 늘 같은 값으로 둔다. 손님은 로그인 뒤 바꾸도록 안내한다.
     */
    public static final String TEMP_PASSWORD = "a123456789!";

    private final AppUserRepository userRepo;
    private final UserSessionRepository sessionRepo;
    private final UserSocialLinkRepository linkRepo;
    private final AppNotificationRepository notificationRepo;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    // ─── 가입 / 로그인 ──────────────────────────────────

    @Transactional
    public AuthResult signup(AuthRequests.Signup req) {
        String loginId = normalizeLoginId(req.loginId());
        String nickname = req.nickname().trim();
        String phone = normalizePhone(req.phone());
        requireEligibleAge(req.birthDate());
        requireLoginIdAvailable(loginId);
        requireNicknameAvailable(nickname, null);
        requirePhoneAvailable(phone, null);

        AppUser user = userRepo.save(AppUser.builder()
                .loginId(loginId)
                .passwordHash(passwordEncoder.encode(req.password()))
                .nickname(nickname)
                .name(req.name().trim())
                .birthDate(req.birthDate())
                .phone(phone)
                .pushOptIn(Boolean.TRUE.equals(req.pushOptIn()))
                .consentAgreed(Boolean.TRUE)
                .lastLoginAt(LocalDateTime.now())
                .build());

        return new AuthResult(issueToken(user), user);
    }

    @Transactional
    public AuthResult login(AuthRequests.Login req) {
        String loginId = normalizeLoginId(req.loginId());
        AppUser user = userRepo.findByLoginIdAndDeletedAtIsNull(loginId).orElse(null);
        // 아이디가 없을 때도 같은 문구로 답해 계정 존재 여부를 흘리지 않는다.
        if (user == null || !user.hasPassword()
                || !passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new IllegalArgumentException("아이디 또는 비밀번호가 올바르지 않아요.");
        }
        user.setLastLoginAt(LocalDateTime.now());
        return new AuthResult(issueToken(user), user);
    }

    /**
     * 시연·테스트용 계정 준비. 같은 아이디가 이미 있으면 아무것도 하지 않는다.
     * (첫 실행 시 DataSeeder 가 호출한다)
     */
    @Transactional
    public void ensureUser(String rawLoginId, String rawPassword, String nickname,
                           String name, LocalDate birthDate, String phone) {
        String loginId = normalizeLoginId(rawLoginId);
        if (userRepo.existsByLoginId(loginId)) return;
        userRepo.save(AppUser.builder()
                .loginId(loginId)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .nickname(uniqueNicknameFrom(nickname))
                .name(name)
                .birthDate(birthDate)
                .phone(normalizePhoneOrNull(phone))
                .pushOptIn(Boolean.FALSE)
                .consentAgreed(Boolean.TRUE)
                .build());
    }

    public boolean isLoginIdAvailable(String rawLoginId) {
        String loginId = normalizeLoginId(rawLoginId);
        return loginId.matches(AuthRequests.LOGIN_ID_PATTERN) && !userRepo.existsByLoginId(loginId);
    }

    public boolean isNicknameAvailable(String rawNickname) {
        String nickname = rawNickname == null ? "" : rawNickname.trim();
        return nickname.matches(AuthRequests.NICKNAME_PATTERN) && !userRepo.existsByNickname(nickname);
    }

    /**
     * 간편로그인.
     *  - 이미 연결된 소셜이면 그 계정으로 로그인한다.
     *  - 처음 보는 소셜인데 로그인 상태(current != null)면 그 계정에 연결한다. (계정 합치기)
     *  - 둘 다 아니면 새 계정을 만든다. 이 계정은 아이디/비밀번호가 없는 상태이며,
     *    마이페이지에서 아이디를 추가하면 두 수단 모두로 로그인할 수 있다.
     */
    @Transactional
    public AuthResult socialLogin(AuthProvider provider,
                                  SocialAuthService.SocialProfile profile,
                                  AppUser current) {
        UserSocialLink link = linkRepo
                .findByProviderAndProviderId(provider, profile.providerId())
                .orElse(null);

        if (link != null) {
            AppUser owner = userRepo.findById(link.getUserId())
                    .filter(u -> u.getDeletedAt() == null)
                    .orElseThrow(() -> new IllegalStateException("연결된 계정을 찾을 수 없어요."));
            if (current != null && !current.getId().equals(owner.getId())) {
                throw new IllegalStateException(
                        "이미 다른 계정에 연결된 " + provider.label() + " 계정이에요.");
            }
            owner.setLastLoginAt(LocalDateTime.now());
            return new AuthResult(issueToken(owner), owner);
        }

        AppUser user = current;
        if (user == null) {
            if (profile.birthDate() != null && !AppUser.isEligibleAge(profile.birthDate())) {
                throw new IllegalArgumentException("만 " + AppUser.MIN_AGE + "세 이상만 가입할 수 있어요.");
            }
            // 제공자가 번호를 준 경우(주로 네이버)에는 여기서 걸러진다. 카카오처럼 번호를 주지 않으면
            // 계정은 일단 만들어지고, 마이페이지에서 번호를 채울 때 updateProfile 이 막는다.
            requirePhoneAvailable(normalizePhoneOrNull(profile.phone()), null);
            user = userRepo.save(AppUser.builder()
                    .name(profile.name() == null || profile.name().isBlank() ? "회원" : profile.name().trim())
                    // 닉네임은 비워 둔다. '담소회원3' 같은 자동 이름 대신 본인이 직접 정하게 하려는 것이고,
                    // 비어 있으면 필수정보 미완료로 잡혀 마이페이지에서 채우기 전까지 신청 화면에 못 들어간다.
                    .nickname(null)
                    .birthDate(profile.birthDate())
                    .phone(normalizePhoneOrNull(profile.phone()))
                    .pushOptIn(Boolean.FALSE)
                    .consentAgreed(Boolean.TRUE)
                    .lastLoginAt(LocalDateTime.now())
                    .build());
        } else if (linkRepo.findByUserIdAndProvider(user.getId(), provider).isPresent()) {
            throw new IllegalStateException("이미 " + provider.label() + " 계정이 연결돼 있어요.");
        } else {
            user.setLastLoginAt(LocalDateTime.now());
        }

        linkRepo.save(UserSocialLink.builder()
                .userId(user.getId())
                .provider(provider)
                .providerId(profile.providerId())
                .build());

        return new AuthResult(issueToken(user), user);
    }

    /** 간편로그인 연결 해제. 로그인 수단이 하나도 남지 않는 해제는 막는다. */
    @Transactional
    public void unlinkSocial(AppUser user, AuthProvider provider) {
        UserSocialLink link = linkRepo.findByUserIdAndProvider(user.getId(), provider)
                .orElseThrow(() -> new IllegalArgumentException("연결되지 않은 로그인 수단이에요."));
        if (!user.hasPassword() && linkRepo.findByUserId(user.getId()).size() <= 1) {
            throw new IllegalStateException("로그인 수단이 하나뿐이라 해제할 수 없어요. 아이디를 먼저 만들어주세요.");
        }
        linkRepo.delete(link);
    }

    public List<AuthProvider> linkedProviders(AppUser user) {
        return linkRepo.findByUserId(user.getId()).stream()
                .map(UserSocialLink::getProvider)
                .toList();
    }

    // ─── 회원 정보 ──────────────────────────────────────

    @Transactional
    public AppUser updateProfile(AppUser user, AuthRequests.ProfileUpdate req) {
        requireEligibleAge(req.birthDate());
        String nickname = req.nickname().trim();
        String phone = normalizePhone(req.phone());
        requireNicknameAvailable(nickname, user.getId());
        // 간편로그인으로 만든 계정이 여기서 번호를 채운다. 그 번호가 이미 다른 계정 것이면
        // 같은 사람이 계정을 하나 더 만든 것이므로 막는다.
        requirePhoneAvailable(phone, user.getId());
        user.setNickname(nickname);
        user.setName(req.name().trim());
        user.setBirthDate(req.birthDate());
        user.setPhone(phone);
        return userRepo.save(user);
    }

    /** 비밀번호 변경 + (아이디가 없던 소셜 전용 계정이면) 아이디 신규 설정. */
    @Transactional
    public AppUser changePassword(AppUser user, AuthRequests.PasswordChange req) {
        if (user.hasPassword()) {
            if (req.currentPassword() == null
                    || !passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())) {
                throw new IllegalArgumentException("현재 비밀번호가 올바르지 않아요.");
            }
        } else {
            String loginId = normalizeLoginId(req.loginId());
            if (!loginId.matches(AuthRequests.LOGIN_ID_PATTERN)) {
                throw new IllegalArgumentException(AuthRequests.LOGIN_ID_MESSAGE + ".");
            }
            requireLoginIdAvailable(loginId);
            user.setLoginId(loginId);
        }
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        // 본인이 바꿨으니 임시 비밀번호 표시는 지운다.
        user.setPasswordResetAt(null);
        return userRepo.save(user);
    }

    @Transactional
    public AppUser setPushOptIn(AppUser user, AuthRequests.PushOptIn req) {
        user.setPushOptIn(Boolean.TRUE.equals(req.pushOptIn()));
        if (req.pushToken() != null && !req.pushToken().isBlank()) {
            user.setPushToken(req.pushToken().trim());
        }
        if (!Boolean.TRUE.equals(user.getPushOptIn())) {
            user.setPushToken(null);
        }
        return userRepo.save(user);
    }

    /** 탈퇴 — 소프트 삭제 + 세션·소셜 연결·알림함 정리. */
    @Transactional
    public void withdraw(AppUser user) {
        user.setDeletedAt(LocalDateTime.now());
        userRepo.save(user);
        sessionRepo.deleteByUserId(user.getId());
        linkRepo.deleteByUserId(user.getId());
        notificationRepo.deleteByUserId(user.getId());
    }

    // ─── 세션 ───────────────────────────────────────────

    @Transactional
    public void logout(String authorization) {
        String token = extractToken(authorization);
        if (token != null) sessionRepo.deleteById(token);
    }

    /** 로그인 필수 API 용. 토큰이 없거나 만료면 401 로 처리되도록 예외를 던진다. */
    public AppUser requireUser(String authorization) {
        AppUser user = currentUser(authorization);
        if (user == null) throw new UnauthorizedException("로그인이 필요해요.");
        return user;
    }

    /** 로그인해도 되고 안 해도 되는 API 용. 유효하지 않으면 null. */
    public AppUser currentUser(String authorization) {
        String token = extractToken(authorization);
        if (token == null) return null;
        UserSession session = sessionRepo.findById(token).orElse(null);
        if (session == null || session.isExpired()) return null;
        AppUser user = userRepo.findById(session.getUserId()).orElse(null);
        if (user == null || user.getDeletedAt() != null) return null;
        return user;
    }

    private String issueToken(AppUser user) {
        sessionRepo.deleteByExpiresAtBefore(LocalDateTime.now());
        String token = UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
        sessionRepo.save(UserSession.builder()
                .token(token)
                .userId(user.getId())
                .expiresAt(LocalDateTime.now().plusDays(SESSION_DAYS))
                .build());
        return token;
    }

    private static String extractToken(String authorization) {
        if (authorization == null || authorization.isBlank()) return null;
        String v = authorization.trim();
        if (v.regionMatches(true, 0, "Bearer ", 0, 7)) v = v.substring(7).trim();
        return v.isEmpty() ? null : v;
    }

    // ─── 관리자용 조회 ──────────────────────────────────

    public List<AppUser> listAll() {
        return userRepo.findByDeletedAtIsNullOrderByCreatedAtDesc();
    }

    public AppUser get(Long id) {
        return userRepo.findById(id)
                .filter(u -> u.getDeletedAt() == null)
                .orElseThrow(() -> new IllegalArgumentException("회원을 찾을 수 없습니다."));
    }

    @Transactional
    public void removeByAdmin(Long id) {
        withdraw(get(id));
    }

    /** 관리자가 회원 정보를 고친다. 검증 규칙은 손님이 직접 고칠 때와 같다. */
    @Transactional
    public AppUser updateProfileByAdmin(Long id, AuthRequests.ProfileUpdate req) {
        return updateProfile(get(id), req);
    }

    /**
     * 관리자가 비밀번호를 초기화한다.
     *  - 임시 비밀번호를 새로 만들어 저장하고, 그 값을 한 번만 돌려준다. (해시만 남으므로 다시 볼 수 없다)
     *  - 쓰던 세션은 모두 끊는다. 남의 기기에 로그인된 채로 남아 있으면 초기화한 의미가 없다.
     *  - 아이디가 없는 간편로그인 전용 계정은 초기화할 대상이 없다.
     */
    @Transactional
    public TempPassword resetPasswordByAdmin(Long id) {
        AppUser user = get(id);
        if (user.getLoginId() == null || user.getLoginId().isBlank()) {
            throw new IllegalStateException(
                    "아이디가 없는 간편로그인 전용 계정이에요. 손님이 마이페이지에서 아이디를 먼저 만들어야 해요.");
        }
        String temp = TEMP_PASSWORD;
        user.setPasswordHash(passwordEncoder.encode(temp));
        user.setPasswordResetAt(LocalDateTime.now());
        userRepo.save(user);
        sessionRepo.deleteByUserId(user.getId());
        return new TempPassword(user.getLoginId(), temp);
    }

    // ─── 공통 ───────────────────────────────────────────

    private void requireLoginIdAvailable(String loginId) {
        if (userRepo.existsByLoginId(loginId)) {
            throw new IllegalStateException("이미 사용 중인 아이디예요.");
        }
    }

    /**
     * 휴대폰번호 중복 검사. selfId 를 주면 본인이 쓰던 번호는 통과시킨다.
     *
     * 번호가 같다고 해서 기존 계정에 자동으로 붙이지는 않는다. 남의 번호를 적어 넣는 것만으로
     * 그 계정을 가져갈 수 있게 되기 때문이다. 본인 확인(문자 인증) 없이는 안내까지만 한다.
     * 이미 만들어 둔 계정 합치기(UserSocialLink)는 로그인한 상태에서 연결하면 동작한다.
     */
    private void requirePhoneAvailable(String phone, Long selfId) {
        if (phone == null || phone.isEmpty()) return;
        boolean takenByOther = userRepo.findByPhoneAndDeletedAtIsNull(phone).stream()
                .anyMatch(u -> !u.getId().equals(selfId));
        if (!takenByOther) return;
        throw new IllegalStateException(
                "이미 가입된 휴대폰번호예요. 기존 계정으로 로그인한 뒤,"
                        + " 마이페이지에서 다른 로그인 수단을 연결해주세요.");
    }

    /** 닉네임 중복 검사. selfId 를 주면 본인이 쓰던 닉네임은 통과시킨다. */
    private void requireNicknameAvailable(String nickname, Long selfId) {
        if (!nickname.matches(AuthRequests.NICKNAME_PATTERN)) {
            throw new IllegalArgumentException(AuthRequests.NICKNAME_MESSAGE + ".");
        }
        if (selfId != null) {
            AppUser self = userRepo.findById(selfId).orElse(null);
            if (self != null && nickname.equals(self.getNickname())) return;
        }
        if (userRepo.existsByNickname(nickname)) {
            throw new IllegalStateException("이미 사용 중인 닉네임이에요.");
        }
    }

    /**
     * 시드 계정용 닉네임 생성. 이미 쓰이고 있으면 뒤에 숫자를 붙여 빈 자리를 찾는다.
     * (손님 가입에는 쓰지 않는다 — 닉네임은 본인이 직접 정한다)
     */
    private String uniqueNicknameFrom(String raw) {
        String base = raw == null ? "" : raw.trim().replaceAll("\\s+", "");
        if (base.length() < 2) base = "담소회원";
        if (base.length() > 10) base = base.substring(0, 10);
        if (!userRepo.existsByNickname(base)) return base;
        for (int i = 2; i < 1000; i++) {
            String candidate = base + i;
            if (!userRepo.existsByNickname(candidate)) return candidate;
        }
        // 여기까지 오면 후보가 바닥난 것 — 시간값으로 유일성을 확보한다.
        return base + System.currentTimeMillis() % 100000;
    }

    private static void requireEligibleAge(LocalDate birthDate) {
        if (birthDate == null) {
            throw new IllegalArgumentException("생년월일을 입력해주세요.");
        }
        if (birthDate.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("생년월일을 확인해주세요.");
        }
        if (!AppUser.isEligibleAge(birthDate)) {
            throw new IllegalArgumentException("만 " + AppUser.MIN_AGE + "세 이상만 가입할 수 있어요.");
        }
    }

    /** 대소문자 구분 없이 같은 아이디로 보이도록 소문자로 통일한다. */
    public static String normalizeLoginId(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }

    /** 숫자만 남긴다. (010-1234-5678 → 01012345678) */
    public static String normalizePhone(String raw) {
        return raw == null ? "" : raw.replaceAll("\\D", "");
    }

    private static String normalizePhoneOrNull(String raw) {
        String p = normalizePhone(raw);
        return p.isEmpty() ? null : p;
    }

    /** 서비스 결과 묶음 (토큰 + 회원). */
    public record AuthResult(String token, AppUser user) {}

    /** 초기화 직후 한 번만 볼 수 있는 임시 비밀번호. */
    public record TempPassword(String loginId, String password) {}

    /** 인증 실패 — {@code GlobalExceptionHandler} 가 401 로 변환한다. */
    public static class UnauthorizedException extends RuntimeException {
        public UnauthorizedException(String message) {
            super(message);
        }
    }
}
