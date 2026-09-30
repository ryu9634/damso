package com.damso.match.api.app.dating;

import com.damso.match.api.app.dating.dto.DatingLikeCardDto;
import com.damso.match.api.app.dating.dto.DatingMeetingDto;
import com.damso.match.api.app.dating.dto.DatingProfileCreateRequest;
import com.damso.match.api.app.dating.dto.DatingProfileDto;
import com.damso.match.api.app.dating.dto.DatingSelfDto;
import com.damso.match.api.app.dating.dto.DatingSelfUpdateRequest;
import com.damso.match.api.app.dating.dto.MeetingGuideDto;
import com.damso.match.domain.dating.dto.DatingQuestionDto;
import com.damso.match.domain.dating.entity.DatingProfile;
import com.damso.match.domain.dating.service.DatingQuestionService;
import com.damso.match.domain.dating.service.DatingService;
import com.damso.match.domain.member.entity.AppUser;
import com.damso.match.domain.member.service.AuthService;
import com.damso.match.domain.setting.service.FeatureService;
import com.damso.match.global.common.FileStorageService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * "담소 셀렉팅"(담소가 해주는 소개팅) API.
 *  - 앱 로그인 필수. 프로필은 계정당 하나이며 Authorization: Bearer 로 본인을 식별한다.
 *  - 프로필 등록 → 이성 프로필 열람 → 찜. 남의 프로필은 조회·수정·찜할 수 없다.
 *  - 관리자가 이 기능을 꺼두면(환경설정 → 앱 기능 사용) 모든 요청을 막는다.
 *  - 사진은 서버 디스크(uploads)에 저장, /uploads/** 로 정적 제공
 */
@RestController
@RequestMapping("/api/dating")
@RequiredArgsConstructor
public class DatingController {

    private final DatingService datingService;
    private final DatingQuestionService questionService;
    private final AuthService authService;
    private final FeatureService featureService;
    private final FileStorageService fileStorage;

    // ─── 상세 질문 (손님 폼 렌더링용) ───────────────────
    @GetMapping("/questions")
    public List<DatingQuestionDto> questions() {
        featureService.requireSelecting();
        return questionService.list().stream().map(DatingQuestionDto::from).toList();
    }

    // ─── 등록 / 본인 확인 ───────────────────────────────
    @PostMapping("/profiles")
    public DatingSelfDto create(@RequestHeader(value = "Authorization", required = false) String auth,
                                @Valid @RequestBody DatingProfileCreateRequest req) {
        featureService.requireSelecting();
        AppUser user = authService.requireUser(auth);
        return selfDto(datingService.create(req, user.getId()));
    }

    /**
     * 로그인한 회원의 프로필.
     * 아직 등록 전이면 200 + 빈 본문(null)을 돌려준다. (앱이 등록 화면으로 보낸다)
     */
    @GetMapping("/profiles/me")
    public DatingSelfDto me(@RequestHeader(value = "Authorization", required = false) String auth) {
        featureService.requireSelecting();
        AppUser user = authService.requireUser(auth);
        DatingProfile p = datingService.findByAccount(user.getId());
        return p == null ? null : selfDto(p);
    }

    /** 계정 도입 전에 등록한 프로필을 이름+연락처로 확인해 지금 계정에 연결한다. */
    @PostMapping("/profiles/link")
    public DatingSelfDto link(@RequestHeader(value = "Authorization", required = false) String auth,
                              @RequestBody LinkRequest req) {
        featureService.requireSelecting();
        AppUser user = authService.requireUser(auth);
        return selfDto(datingService.linkToAccount(user.getId(), req.name, req.contact));
    }

    /** 본인 프로필 (이름·연락처 + 작성한 설문 답변 포함). */
    @GetMapping("/profiles/{id}")
    public DatingSelfDto self(@RequestHeader(value = "Authorization", required = false) String auth,
                              @PathVariable Long id) {
        featureService.requireSelecting();
        AppUser user = authService.requireUser(auth);
        return selfDto(datingService.requireOwned(id, user.getId()));
    }

    /** 본인 정보 수정 — 기본 정보 + 설문 답변 + 사진. 성별·상태는 그대로 둔다. */
    @PutMapping("/profiles/{id}")
    public DatingSelfDto updateSelf(@RequestHeader(value = "Authorization", required = false) String auth,
                                    @PathVariable Long id,
                                    @Valid @RequestBody DatingSelfUpdateRequest req) {
        featureService.requireSelecting();
        AppUser user = authService.requireUser(auth);
        datingService.requireOwned(id, user.getId());
        datingService.selfUpdate(id, req);
        return selfDto(datingService.get(id));
    }

    private DatingSelfDto selfDto(DatingProfile p) {
        return DatingSelfDto.from(p, datingService.answersOf(p.getId()));
    }

    // ─── 이성 프로필 열람 / 찜 ──────────────────────────
    @GetMapping("/profiles/{id}/browse")
    public List<DatingProfileDto> browse(@RequestHeader(value = "Authorization", required = false) String auth,
                                         @PathVariable Long id) {
        featureService.requireSelecting();
        AppUser user = authService.requireUser(auth);
        datingService.requireOwned(id, user.getId());
        return datingService.browse(id);
    }

    /** 좋아요 보내기 — 상대에게 알림이 간다. */
    @PostMapping("/profiles/{id}/likes/{targetId}")
    public void like(@RequestHeader(value = "Authorization", required = false) String auth,
                     @PathVariable Long id, @PathVariable Long targetId) {
        featureService.requireSelecting();
        AppUser user = authService.requireUser(auth);
        datingService.requireOwned(id, user.getId());
        datingService.like(id, targetId);
    }

    /** 좋아요 취소 — 상대가 아직 답하기 전에만 된다. */
    @DeleteMapping("/profiles/{id}/likes/{targetId}")
    public void unlike(@RequestHeader(value = "Authorization", required = false) String auth,
                       @PathVariable Long id, @PathVariable Long targetId) {
        featureService.requireSelecting();
        AppUser user = authService.requireUser(auth);
        datingService.requireOwned(id, user.getId());
        datingService.unlike(id, targetId);
    }

    // ─── 관심 없음 ──────────────────────────────────────
    /** 관심 없음 — 내 목록에서만 치운다. 상대에게는 알리지 않는다. */
    @PostMapping("/profiles/{id}/passes/{targetId}")
    public void pass(@RequestHeader(value = "Authorization", required = false) String auth,
                     @PathVariable Long id, @PathVariable Long targetId) {
        featureService.requireSelecting();
        AppUser user = authService.requireUser(auth);
        datingService.requireOwned(id, user.getId());
        datingService.pass(id, targetId);
    }

    /** 치워둔 프로필 되살리기 (전체). */
    @DeleteMapping("/profiles/{id}/passes")
    public Map<String, Integer> clearPasses(@RequestHeader(value = "Authorization", required = false) String auth,
                                            @PathVariable Long id) {
        featureService.requireSelecting();
        AppUser user = authService.requireUser(auth);
        datingService.requireOwned(id, user.getId());
        datingService.clearPasses(id);
        return Map.of("passed", 0);
    }

    /** 지금 치워둔 프로필 수 — 화면에 '다시 보기' 버튼을 띄울지 판단용. */
    @GetMapping("/profiles/{id}/passes/count")
    public Map<String, Integer> passCount(@RequestHeader(value = "Authorization", required = false) String auth,
                                          @PathVariable Long id) {
        featureService.requireSelecting();
        AppUser user = authService.requireUser(auth);
        datingService.requireOwned(id, user.getId());
        return Map.of("passed", datingService.passCount(id));
    }

    // ─── 노출 on/off ────────────────────────────────────
    /** 본인 프로필 노출 스위치. visible=false 면 목록에서 빠지고 나도 열람할 수 없다. */
    @PatchMapping("/profiles/{id}/visibility")
    public DatingSelfDto visibility(@RequestHeader(value = "Authorization", required = false) String auth,
                                    @PathVariable Long id,
                                    @RequestBody VisibilityRequest req) {
        featureService.requireSelecting();
        AppUser user = authService.requireUser(auth);
        datingService.requireOwned(id, user.getId());
        return selfDto(datingService.setVisible(id, !Boolean.FALSE.equals(req.visible)));
    }

    public static class VisibilityRequest {
        public Boolean visible;
    }

    // ─── 받은 / 보낸 좋아요 ─────────────────────────────
    /** 나에게 온 좋아요 목록. (마이페이지 → 받은 좋아요) */
    @GetMapping("/profiles/{id}/likes/received")
    public List<DatingLikeCardDto> received(@RequestHeader(value = "Authorization", required = false) String auth,
                                            @PathVariable Long id) {
        featureService.requireSelecting();
        AppUser user = authService.requireUser(auth);
        datingService.requireOwned(id, user.getId());
        return datingService.receivedLikes(id);
    }

    /** 내가 보낸 좋아요 목록. */
    @GetMapping("/profiles/{id}/likes/sent")
    public List<DatingLikeCardDto> sent(@RequestHeader(value = "Authorization", required = false) String auth,
                                        @PathVariable Long id) {
        featureService.requireSelecting();
        AppUser user = authService.requireUser(auth);
        datingService.requireOwned(id, user.getId());
        return datingService.sentLikes(id);
    }

    /** 받은 좋아요에 답하기. accept=true 면 좋아요, false 면 미안해요. */
    @PostMapping("/profiles/{id}/likes/{likeId}/respond")
    public List<DatingLikeCardDto> respond(@RequestHeader(value = "Authorization", required = false) String auth,
                                           @PathVariable Long id, @PathVariable Long likeId,
                                           @RequestBody RespondRequest req) {
        featureService.requireSelecting();
        AppUser user = authService.requireUser(auth);
        datingService.requireOwned(id, user.getId());
        datingService.respondToLike(id, likeId, Boolean.TRUE.equals(req.accept));
        return datingService.receivedLikes(id);
    }

    // ─── 만남 약속 ──────────────────────────────────────
    /** 약속 화면 안내 — 장소·예약금 문구·예약 링크. 관리자 환경설정에서 온다. */
    @GetMapping("/meeting-guide")
    public MeetingGuideDto meetingGuide() {
        featureService.requireSelecting();
        return datingService.meetingGuide();
    }

    /** 약속 제안 (서로 좋아요가 된 상대에게만). 날짜+시간 후보 최대 3개. */
    @PutMapping("/profiles/{id}/meetings/{otherId}")
    public DatingMeetingDto propose(@RequestHeader(value = "Authorization", required = false) String auth,
                                    @PathVariable Long id, @PathVariable Long otherId,
                                    @RequestBody DatingMeetingDto.ProposeRequest req) {
        featureService.requireSelecting();
        AppUser user = authService.requireUser(auth);
        datingService.requireOwned(id, user.getId());
        return datingService.proposeMeeting(id, otherId, req.slots());
    }

    /** 상대가 제안한 후보 중 하나를 골라 확정. */
    @PostMapping("/profiles/{id}/meetings/{otherId}/confirm")
    public DatingMeetingDto confirm(@RequestHeader(value = "Authorization", required = false) String auth,
                                    @PathVariable Long id, @PathVariable Long otherId,
                                    @RequestBody(required = false) DatingMeetingDto.ConfirmRequest req) {
        featureService.requireSelecting();
        AppUser user = authService.requireUser(auth);
        datingService.requireOwned(id, user.getId());
        return datingService.confirmMeeting(id, otherId,
                req == null ? null : req.day(), req == null ? null : req.hour());
    }

    /** 약속 취소. */
    @DeleteMapping("/profiles/{id}/meetings/{otherId}")
    public DatingMeetingDto cancelMeeting(@RequestHeader(value = "Authorization", required = false) String auth,
                                          @PathVariable Long id, @PathVariable Long otherId) {
        featureService.requireSelecting();
        AppUser user = authService.requireUser(auth);
        datingService.requireOwned(id, user.getId());
        return datingService.cancelMeeting(id, otherId);
    }

    /** 받은 좋아요 답변 요청. */
    public static class RespondRequest {
        public Boolean accept;
    }

    /** 기존 프로필 연결 요청. */
    public static class LinkRequest {
        public String name;
        public String contact;
    }

    // ─── 사진 업로드 ────────────────────────────────────
    @PostMapping("/photo")
    public Map<String, String> uploadPhoto(@RequestParam("file") MultipartFile file) {
        featureService.requireSelecting();
        return Map.of("url", fileStorage.storeImage(file));
    }
}
