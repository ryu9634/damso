package com.damso.match.domain.member.dto;

import jakarta.validation.constraints.*;

import java.time.LocalDate;

/** 앱 로그인/회원 관련 요청 묶음. */
public final class AuthRequests {

    private AuthRequests() {}

    /** 아이디 규칙 — 영문 소문자/숫자/._- 4~20자. (대문자로 보내도 서버가 소문자로 바꾼다) */
    public static final String LOGIN_ID_PATTERN = "^[A-Za-z0-9._-]{4,20}$";
    public static final String LOGIN_ID_MESSAGE = "아이디는 영문·숫자·._- 조합 4~20자예요";
    public static final String PASSWORD_MESSAGE = "비밀번호는 8자 이상이어야 해요";

    /** 닉네임 규칙 — 공백 없이 2~12자. (한글·영문·숫자 모두 허용) */
    public static final String NICKNAME_PATTERN = "^\\S{2,12}$";
    public static final String NICKNAME_MESSAGE = "닉네임은 공백 없이 2~12자예요";

    /**
     * 휴대폰번호 규칙 — 숫자만 남긴 010/011… 형태.
     * 화면에서 하이픈을 지우고 보내므로 서버는 숫자만 받는다고 본다.
     */
    public static final String PHONE_PATTERN = "^01[0-9]{8,9}$";
    public static final String PHONE_MESSAGE = "휴대폰번호를 정확히 입력해주세요";

    /**
     * 회원가입.
     *  - 로그인 수단: 아이디 + 비밀번호
     *  - 공개 이름: 닉네임 (방명록 등 다른 손님에게 보이는 이름)
     *  - 회원 확인용 필수 정보: 성명, 생년월일(만 18세 이상), 휴대폰번호
     *  - 휴대폰번호는 매칭 안내·연락에 쓰이므로 필수다.
     *  - 만 18세 검증은 오늘 날짜 기준이라 서비스에서 수행한다.
     */
    public record Signup(
            @NotBlank(message = "아이디를 입력해주세요")
            @Pattern(regexp = LOGIN_ID_PATTERN, message = LOGIN_ID_MESSAGE)
            String loginId,

            @NotBlank(message = "비밀번호를 입력해주세요")
            @Size(min = 8, max = 72, message = PASSWORD_MESSAGE)
            String password,

            @NotBlank(message = "닉네임을 입력해주세요")
            @Pattern(regexp = NICKNAME_PATTERN, message = NICKNAME_MESSAGE)
            String nickname,

            @NotBlank(message = "성명을 입력해주세요") @Size(max = 30, message = "성명은 최대 30자입니다") String name,
            @NotNull(message = "생년월일을 입력해주세요") LocalDate birthDate,

            @NotBlank(message = PHONE_MESSAGE)
            @Pattern(regexp = PHONE_PATTERN, message = PHONE_MESSAGE)
            String phone,

            Boolean pushOptIn,
            @NotNull @AssertTrue(message = "이용약관 및 개인정보 수집·이용에 동의해야 합니다") Boolean agreedPrivacy
    ) {}

    /** 아이디 로그인. */
    public record Login(
            @NotBlank(message = "아이디를 입력해주세요") String loginId,
            @NotBlank(message = "비밀번호를 입력해주세요") String password
    ) {}

    /** 마이페이지 정보 수정 / 소셜 가입 후 필수정보 보완. */
    public record ProfileUpdate(
            @NotBlank(message = "닉네임을 입력해주세요")
            @Pattern(regexp = NICKNAME_PATTERN, message = NICKNAME_MESSAGE)
            String nickname,

            @NotBlank(message = "성명을 입력해주세요") @Size(max = 30, message = "성명은 최대 30자입니다") String name,
            @NotNull(message = "생년월일을 입력해주세요") LocalDate birthDate,

            @NotBlank(message = PHONE_MESSAGE)
            @Pattern(regexp = PHONE_PATTERN, message = PHONE_MESSAGE)
            String phone
    ) {}

    /** 비밀번호 변경. 간편로그인만 쓰던 계정이 아이디를 새로 만들 때도 쓴다. */
    public record PasswordChange(
            String currentPassword,
            @NotBlank(message = "새 비밀번호를 입력해주세요")
            @Size(min = 8, max = 72, message = PASSWORD_MESSAGE)
            String newPassword,
            /** 아이디가 아직 없는 계정(간편로그인 전용)일 때만 사용한다. */
            @Pattern(regexp = "^$|" + LOGIN_ID_PATTERN, message = LOGIN_ID_MESSAGE)
            String loginId
    ) {}

    /** 푸시 알람 허용 여부 변경. */
    public record PushOptIn(
            @NotNull Boolean pushOptIn,
            @Size(max = 255) String pushToken
    ) {}

    /**
     * 소셜 로그인/연동 콜백 — 제공자에게 받은 인가 코드를 서버가 대신 교환한다.
     *  - link=true 이고 로그인 상태면 새 계정을 만들지 않고 현재 계정에 연결한다.
     */
    public record SocialLogin(
            @NotBlank(message = "인가 코드가 없습니다") String code,
            String state,
            String redirectUri,
            Boolean link
    ) {}
}
