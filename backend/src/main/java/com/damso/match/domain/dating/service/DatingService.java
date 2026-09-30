package com.damso.match.domain.dating.service;

import com.damso.match.api.admin.dating.dto.DatingAdminDto;
import com.damso.match.api.admin.dating.dto.DatingLikeDto;
import com.damso.match.api.admin.dating.dto.DatingPairNoteDto;
import com.damso.match.api.app.dating.dto.DatingLikeCardDto;
import com.damso.match.api.app.dating.dto.DatingMeetingDto;
import com.damso.match.api.app.dating.dto.DatingProfileCreateRequest;
import com.damso.match.api.app.dating.dto.DatingProfileDto;
import com.damso.match.api.app.dating.dto.DatingSelfUpdateRequest;
import com.damso.match.api.app.dating.dto.MeetingGuideDto;
import com.damso.match.domain.dating.dto.DatingAnswerView;
import com.damso.match.domain.dating.entity.DatingAnswer;
import com.damso.match.domain.dating.entity.DatingLike;
import com.damso.match.domain.dating.entity.DatingLikeStatus;
import com.damso.match.domain.dating.entity.DatingPairNote;
import com.damso.match.domain.dating.entity.DatingPass;
import com.damso.match.domain.dating.entity.DatingProfile;
import com.damso.match.domain.dating.entity.DatingQuestion;
import com.damso.match.domain.dating.entity.DatingStatus;
import com.damso.match.domain.dating.entity.MeetingStatus;
import com.damso.match.domain.dating.repository.DatingAnswerRepository;
import com.damso.match.domain.dating.repository.DatingLikeRepository;
import com.damso.match.domain.dating.repository.DatingMatchRepository;
import com.damso.match.domain.dating.repository.DatingPairNoteRepository;
import com.damso.match.domain.dating.repository.DatingPassRepository;
import com.damso.match.domain.dating.repository.DatingProfileRepository;
import com.damso.match.domain.dating.repository.DatingQuestionRepository;
import com.damso.match.domain.notification.entity.NotificationType;
import com.damso.match.domain.notification.service.NotificationService;
import com.damso.match.domain.setting.service.SettingService;
import com.damso.match.domain.setting.service.SettingsCatalog;
import com.damso.match.global.common.Gender;
import com.damso.match.api.admin.dating.dto.DatingAdminRequests.ProfileUpdate;
import com.damso.match.api.app.dating.dto.DatingProfileCreateRequest.AnswerInput;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * "담소가 해주는 소개팅".
 *  - 손님이 이름/연락처/기본정보 + 커스텀 질문 답변을 등록하면 이성 프로필을 열람할 수 있다.
 *  - 손님이 '좋아요'를 보내면 상대에게 알림이 가고, 받은 사람은 좋아요/미안해요로 답한다.
 *    서로 좋아요가 되면 두 사람이 앱에서 만남 약속을 잡을 수 있다.
 *  - 자동 매칭은 없다. 관리자도 진행 상황을 보고 수동으로 도울 수 있다.
 *  - 관리자는 상태(DatingStatus)/메모로 진행 상황을 추적하고, 삭제는 소프트 삭제로 처리한다.
 */
@Service
@RequiredArgsConstructor
public class DatingService {

    private final DatingProfileRepository repo;
    private final DatingLikeRepository likeRepo;
    private final DatingPassRepository passRepo;
    private final DatingQuestionRepository questionRepo;
    private final DatingAnswerRepository answerRepo;
    private final DatingMatchRepository matchRepo;
    private final DatingPairNoteRepository pairNoteRepo;
    private final NotificationService notifications;
    private final SettingService settings;

    // ─── 등록 / 로그인 ──────────────────────────────────
    /**
     * 프로필 등록. 로그인한 앱 회원만 만들 수 있고, 계정당 하나만 가진다.
     *
     * @param appUserId 등록하는 앱 회원 id
     */
    @Transactional
    public DatingProfile create(DatingProfileCreateRequest req, Long appUserId) {
        repo.findByAppUserIdAndDeletedAtIsNull(appUserId).ifPresent(p -> {
            throw new IllegalStateException("이미 등록한 프로필이 있어요.");
        });

        List<DatingQuestion> questions = questionRepo.findAllByOrderBySortOrderAsc();
        Map<Long, String> answers = validatedAnswers(req.answers(), questions);

        DatingProfile p = repo.save(DatingProfile.builder()
                .appUserId(appUserId)
                .name(req.name().trim())
                .contact(req.contact().trim())
                .birthYear(req.birthYear())
                .gender(req.gender())
                .photoUrl(emptyToNull(req.photoUrl()))
                .consentAgreed(Boolean.TRUE.equals(req.agreedPrivacy()))
                .status(DatingStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build());

        saveAnswers(p.getId(), answers, questions);
        return p;
    }

    /** 로그인한 앱 회원의 프로필. 없으면 null. */
    @Transactional(readOnly = true)
    public DatingProfile findByAccount(Long appUserId) {
        return repo.findByAppUserIdAndDeletedAtIsNull(appUserId).orElse(null);
    }

    /**
     * 계정 도입 전에 등록한 프로필을 지금 계정에 연결한다.
     * 이름 + 연락처가 정확히 맞아야 하고, 이미 다른 계정에 연결된 프로필은 가져올 수 없다.
     */
    @Transactional
    public DatingProfile linkToAccount(Long appUserId, String name, String contact) {
        if (repo.findByAppUserIdAndDeletedAtIsNull(appUserId).isPresent()) {
            throw new IllegalStateException("이미 연결된 프로필이 있어요.");
        }
        DatingProfile p = repo.findByNameAndContactAndDeletedAtIsNull(
                        name == null ? "" : name.trim(),
                        contact == null ? "" : contact.trim())
                .orElseThrow(() -> new IllegalArgumentException("일치하는 프로필이 없어요. 이름과 연락처를 확인해주세요."));
        if (p.getAppUserId() != null && !p.getAppUserId().equals(appUserId)) {
            throw new IllegalStateException("이미 다른 계정에 연결된 프로필이에요.");
        }
        p.setAppUserId(appUserId);
        return repo.save(p);
    }

    /**
     * 셀렉팅을 이용할 수 있는 상태인지 확인.
     * 심사중·휴면·제외면 상태에 맞는 안내와 함께 막는다. (화면에서 감추는 것과 별개로 서버에서도 막는다)
     * 본인이 노출을 꺼둔 경우도 같이 막는다 — 내가 안 보이는데 남을 고르는 건 공평하지 않다.
     */
    @Transactional(readOnly = true)
    public void requireSelectable(DatingProfile me) {
        DatingStatus status = DatingStatus.orDefault(me.getStatus());
        if (me.getDeletedAt() != null || !status.isBrowsable()) {
            String notice = status.notice();
            throw new IllegalStateException(
                    notice == null ? "지금은 담소 셀렉팅을 이용할 수 없어요." : notice);
        }
        if (me.isHidden()) throw new IllegalStateException(DatingProfile.HIDDEN_NOTICE);
    }

    /** 본인 프로필인지 확인 — 남의 프로필을 고치거나 찜하지 못하게 막는다. */
    @Transactional(readOnly = true)
    public DatingProfile requireOwned(Long profileId, Long appUserId) {
        DatingProfile p = get(profileId);
        if (p.getAppUserId() == null || !p.getAppUserId().equals(appUserId)) {
            throw new IllegalArgumentException("본인 프로필만 이용할 수 있어요.");
        }
        return p;
    }

    /** 손님용 조회 — 삭제된 프로필은 없는 것으로 취급. */
    @Transactional(readOnly = true)
    public DatingProfile get(Long id) {
        DatingProfile p = getRaw(id);
        if (p.getDeletedAt() != null) throw new IllegalArgumentException("프로필을 찾을 수 없습니다.");
        return p;
    }

    /** 관리자용 조회 — 삭제된 프로필도 포함. */
    @Transactional(readOnly = true)
    public DatingProfile getRaw(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("프로필을 찾을 수 없습니다."));
    }

    // ─── 이성 프로필 열람 ───────────────────────────────
    /**
     * me 의 이성 프로필 목록 (최신 등록순). liked 여부 + 답변 포함.
     * 삭제·제외·노출 off 회원과 내가 '관심 없음'으로 치운 회원은 빠진다.
     */
    @Transactional
    public List<DatingProfileDto> browse(Long meId) {
        DatingProfile me = get(meId);
        requireSelectable(me);
        expireStaleLikes();
        Gender opposite = me.getGender() == Gender.MALE ? Gender.FEMALE : Gender.MALE;
        // 기간이 만료된 좋아요는 다시 보낼 수 있으니 '이미 선택함'으로 치지 않는다.
        Set<Long> myLikes = likeRepo.findByFromProfileId(meId).stream()
                .filter(l -> l.statusOrDefault() != DatingLikeStatus.EXPIRED)
                .map(DatingLike::getToProfileId).collect(Collectors.toSet());
        Set<Long> passed = passedIds(meId);

        List<DatingQuestion> questions = questionRepo.findAllByOrderBySortOrderAsc();
        Map<Long, Map<Long, String>> answers = answersByProfile();

        return repo.findByGenderAndDeletedAtIsNullOrderByCreatedAtDesc(opposite).stream()
                .filter(DatingProfile::isActive)
                .filter(p -> !passed.contains(p.getId()))
                .map(p -> new DatingProfileDto(
                        p.getId(), p.getGender(), p.getBirthYear(), p.getPhotoUrl(),
                        answerViews(p.getId(), questions, answers), myLikes.contains(p.getId()),
                        p.isVerified()))
                .toList();
    }

    // ─── 관심 없음 ──────────────────────────────────────
    /** 내가 치워둔 프로필 id 들. */
    private Set<Long> passedIds(Long meId) {
        return passRepo.findByFromProfileId(meId).stream()
                .map(DatingPass::getToProfileId).collect(Collectors.toSet());
    }

    /** 내가 치워둔 프로필 수 — 화면에 "다시 보기" 버튼을 보여줄지 판단할 때 쓴다. */
    @Transactional(readOnly = true)
    public int passCount(Long meId) {
        return passRepo.findByFromProfileId(meId).size();
    }

    /**
     * 관심 없음 — 이 프로필을 내 목록에서만 치운다.
     * 상대에게는 알리지 않는다. 이미 좋아요를 보낸 상대는 치울 수 없다.
     */
    @Transactional
    public void pass(Long fromId, Long toId) {
        if (fromId.equals(toId)) throw new IllegalArgumentException("본인 프로필은 치울 수 없어요.");
        requireSelectable(get(fromId));
        get(toId);
        DatingLike sent = likeRepo.findByFromProfileIdAndToProfileId(fromId, toId).orElse(null);
        if (sent != null && sent.statusOrDefault() != DatingLikeStatus.EXPIRED) {
            throw new IllegalStateException("이미 좋아요를 보낸 상대예요.");
        }
        if (passRepo.findByFromProfileIdAndToProfileId(fromId, toId).isPresent()) return;
        passRepo.save(DatingPass.builder()
                .fromProfileId(fromId).toProfileId(toId)
                .createdAt(LocalDateTime.now()).build());
    }

    /** 치워둔 프로필을 한 번에 되살린다. */
    @Transactional
    public void clearPasses(Long meId) {
        passRepo.deleteByFromProfileId(meId);
    }

    // ─── 노출 on/off (본인) ─────────────────────────────
    /**
     * 본인이 프로필 노출을 켜고 끈다.
     * 심사중·제외처럼 관리자가 막아둔 상태는 본인이 켠다고 풀리지 않는다.
     */
    @Transactional
    public DatingProfile setVisible(Long profileId, boolean visible) {
        DatingProfile p = get(profileId);
        if (visible && !DatingStatus.orDefault(p.getStatus()).isBrowsable()) {
            String notice = DatingStatus.orDefault(p.getStatus()).notice();
            throw new IllegalStateException(notice == null ? "지금은 노출을 켤 수 없어요." : notice);
        }
        p.setHiddenAt(visible ? null : LocalDateTime.now());
        return repo.save(p);
    }

    // ─── 매장 확인 배지 (관리자) ────────────────────────
    /** 매장에서 직접 얼굴을 확인했는지 표시한다. */
    @Transactional
    public DatingProfile setVerified(Long profileId, boolean verified) {
        DatingProfile p = getRaw(profileId);
        p.setVerifiedAt(verified ? LocalDateTime.now() : null);
        return repo.save(p);
    }

    // ─── 좋아요 ─────────────────────────────────────────
    /** 한 사람이 하루에 선택할 수 있는 인원 수. */
    public static final int DAILY_LIKE_LIMIT = 2;

    /** 받은 좋아요에 답할 수 있는 시간. 지나면 자동으로 닫힌다. */
    public static final int LIKE_EXPIRE_HOURS = 72;

    /**
     * 답변 기한이 지난 좋아요를 닫는다.
     *  - 보낸 사람에게만 알린다. (받은 사람은 이미 답하지 않기로 한 셈이라 다시 찌르지 않는다)
     *  - 목록을 열 때마다 불러 화면과 DB 가 어긋나지 않게 하고, 스케줄러로도 주기적으로 돈다.
     *
     * @return 이번에 닫힌 건수
     */
    @Transactional
    public int expireStaleLikes() {
        LocalDateTime deadline = LocalDateTime.now().minusHours(LIKE_EXPIRE_HOURS);
        List<DatingLike> stale = likeRepo.findByStatus(DatingLikeStatus.PENDING).stream()
                .filter(l -> l.getCreatedAt() != null && l.getCreatedAt().isBefore(deadline))
                .toList();

        for (DatingLike l : stale) {
            l.setStatus(DatingLikeStatus.EXPIRED);
            l.setRespondedAt(LocalDateTime.now());
            likeRepo.save(l);
            notifications.notifyProfileOwner(l.getFromProfileId(), NotificationType.LIKE_DECLINED,
                    "답변 기간이 지났어요",
                    LIKE_EXPIRE_HOURS + "시간 동안 답이 없어 자동으로 닫혔어요. 다른 분을 만나보세요.",
                    LIKES_LINK);
        }
        return stale.size();
    }

    /** 이 좋아요의 답변 기한. */
    private LocalDateTime expiresAt(DatingLike like) {
        return like.getCreatedAt() == null ? null : like.getCreatedAt().plusHours(LIKE_EXPIRE_HOURS);
    }

    /** 좋아요를 눌렀을 때 상대가 받는 알림에서 쓰는 링크. */
    private static final String LIKES_LINK = "/likes";

    /**
     * 좋아요 보내기.
     *  - 상대에게 알림이 간다.
     *  - 상대가 이미 나를 좋아요 했다면 답을 기다릴 것 없이 서로 좋아요로 잇고 양쪽에 알린다.
     */
    @Transactional
    public void like(Long fromId, Long toId) {
        if (fromId.equals(toId)) throw new IllegalArgumentException("본인에게는 좋아요를 보낼 수 없습니다.");
        requireSelectable(get(fromId));   // 삭제됐거나 심사중·휴면·제외·노출 off 면 여기서 걸린다
        DatingProfile target = get(toId);
        if (!target.isActive()) throw new IllegalArgumentException("지금은 좋아요를 보낼 수 없는 프로필입니다.");

        DatingLike before = likeRepo.findByFromProfileIdAndToProfileId(fromId, toId).orElse(null);
        if (before != null) {
            // 기간이 지나 닫힌 건은 다시 보낼 수 있다. 그 외(대기·좋아요·미안해요)는 그대로 둔다.
            if (before.statusOrDefault() != DatingLikeStatus.EXPIRED) return;
            likeRepo.delete(before);
            likeRepo.flush();
        }
        // 치워뒀던 상대에게 마음이 바뀌었다면 '관심 없음'도 함께 푼다.
        passRepo.findByFromProfileIdAndToProfileId(fromId, toId).ifPresent(passRepo::delete);

        if (todayLikeCount(fromId) >= DAILY_LIKE_LIMIT) {
            throw new IllegalArgumentException(
                    "하루에 " + DAILY_LIKE_LIMIT + "명까지만 선택할 수 있어요. 내일 다시 선택해주세요.");
        }

        // 상대가 먼저 보낸 좋아요가 살아 있으면(대기·수락) 바로 서로 좋아요로 잇는다.
        DatingLike back = likeRepo.findByFromProfileIdAndToProfileId(toId, fromId).orElse(null);
        boolean mutual = back != null
                && back.statusOrDefault() != DatingLikeStatus.DECLINED
                && back.statusOrDefault() != DatingLikeStatus.EXPIRED;

        DatingLike mine = likeRepo.save(DatingLike.builder()
                .fromProfileId(fromId).toProfileId(toId)
                .status(mutual ? DatingLikeStatus.ACCEPTED : DatingLikeStatus.PENDING)
                .respondedAt(mutual ? LocalDateTime.now() : null)
                .createdAt(LocalDateTime.now()).build());

        if (!mutual) {
            notifications.notifyProfileOwner(toId, NotificationType.LIKE_RECEIVED,
                    "좋아요를 받았어요",
                    "마이페이지 > 받은 좋아요에서 확인하고 답해보세요.",
                    LIKES_LINK);
            return;
        }

        // 서로 좋아요 — 상대가 먼저 보낸 좋아요도 함께 수락 처리한다.
        back.setStatus(DatingLikeStatus.ACCEPTED);
        back.setRespondedAt(mine.getRespondedAt());
        likeRepo.save(back);

        notifications.notifyProfileOwner(toId, NotificationType.LIKE_ACCEPTED,
                "서로 좋아요가 됐어요!",
                "마음이 통했어요. 이제 만남 약속을 잡을 수 있어요.", LIKES_LINK);
        notifications.notifyProfileOwner(fromId, NotificationType.LIKE_ACCEPTED,
                "서로 좋아요가 됐어요!",
                "상대도 이미 좋아요를 보냈었어요. 만남 약속을 잡아보세요.", LIKES_LINK);
    }

    /**
     * 받은 좋아요에 답하기 (좋아요 / 미안해요).
     * 본인에게 온 좋아요만, 아직 답하지 않은 건에 대해서만 답할 수 있다.
     */
    @Transactional
    public DatingLikeStatus respondToLike(Long meProfileId, Long likeId, boolean accept) {
        DatingLike like = likeRepo.findById(likeId)
                .orElseThrow(() -> new IllegalArgumentException("좋아요를 찾을 수 없어요."));
        if (!like.getToProfileId().equals(meProfileId)) {
            throw new IllegalArgumentException("나에게 온 좋아요만 답할 수 있어요.");
        }
        if (like.statusOrDefault() != DatingLikeStatus.PENDING) {
            throw new IllegalStateException("이미 답한 좋아요예요.");
        }

        like.setStatus(accept ? DatingLikeStatus.ACCEPTED : DatingLikeStatus.DECLINED);
        like.setRespondedAt(LocalDateTime.now());
        likeRepo.save(like);

        if (accept) {
            notifications.notifyProfileOwner(like.getFromProfileId(), NotificationType.LIKE_ACCEPTED,
                    "좋아요가 이어졌어요!",
                    "상대가 좋아요로 답했어요. 이제 만남 약속을 잡을 수 있어요.", LIKES_LINK);
        } else {
            notifications.notifyProfileOwner(like.getFromProfileId(), NotificationType.LIKE_DECLINED,
                    "이번엔 인연이 아니었어요",
                    "아쉽지만 다른 분과의 만남을 기다려볼까요?", LIKES_LINK);
        }
        return like.statusOrDefault();
    }

    /** 내가 받은 좋아요 목록 (최신순). */
    @Transactional
    public List<DatingLikeCardDto> receivedLikes(Long meProfileId) {
        expireStaleLikes();
        return likeCards(meProfileId, likeRepo.findByToProfileId(meProfileId), true);
    }

    /** 내가 보낸 좋아요 목록 (최신순). */
    @Transactional
    public List<DatingLikeCardDto> sentLikes(Long meProfileId) {
        expireStaleLikes();
        return likeCards(meProfileId, likeRepo.findByFromProfileId(meProfileId), false);
    }

    /** 좋아요 기록을 화면용 카드로. received=true 면 상대는 보낸 사람이다. */
    private List<DatingLikeCardDto> likeCards(Long meProfileId, List<DatingLike> likes, boolean received) {
        List<DatingQuestion> questions = questionRepo.findAllByOrderBySortOrderAsc();
        Map<Long, Map<Long, String>> answers = answersByProfile();

        List<DatingLikeCardDto> out = new ArrayList<>();
        for (DatingLike l : likes) {
            Long otherId = received ? l.getFromProfileId() : l.getToProfileId();
            DatingProfile other = repo.findById(otherId).orElse(null);
            // 탈퇴·삭제된 상대는 목록에서 뺀다.
            if (other == null || other.getDeletedAt() != null) continue;

            boolean accepted = l.isAccepted();
            boolean pending = l.statusOrDefault() == DatingLikeStatus.PENDING;
            out.add(new DatingLikeCardDto(
                    l.getId(), other.getId(),
                    accepted ? other.getName() : null,
                    other.getGender(), other.getBirthYear(), other.getPhotoUrl(),
                    answerViews(other.getId(), questions, answers),
                    l.getCreatedAt(),
                    l.statusOrDefault(), l.statusOrDefault().label(), l.getRespondedAt(),
                    accepted ? meeting(meProfileId, other.getId()) : null,
                    other.isVerified(),
                    pending ? expiresAt(l) : null));
        }
        out.sort(Comparator.comparing(DatingLikeCardDto::createdAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return out;
    }

    /**
     * 오늘 이 사람이 선택한 인원 수.
     * 선택을 취소하면 그 자리는 다시 비는다 — 취소 이력은 남기지 않는다.
     */

    /**
     * 오늘 이 사람이 선택한 인원 수.
     * 선택을 취소하면 그 자리는 다시 비는다 — 취소 이력은 남기지 않는다.
     */
    @Transactional(readOnly = true)
    public int todayLikeCount(Long profileId) {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        return (int) likeRepo.findByFromProfileId(profileId).stream()
                .filter(l -> l.getCreatedAt() != null && !l.getCreatedAt().isBefore(startOfDay))
                .count();
    }

    /** 좋아요 취소 — 상대가 아직 답하기 전에만 할 수 있다. */
    @Transactional
    public void unlike(Long fromId, Long toId) {
        DatingLike like = likeRepo.findByFromProfileIdAndToProfileId(fromId, toId).orElse(null);
        if (like == null) return;
        if (like.statusOrDefault() != DatingLikeStatus.PENDING) {
            throw new IllegalStateException("상대가 이미 답한 좋아요는 취소할 수 없어요.");
        }
        likeRepo.delete(like);
    }

    // ─── 만남 약속 ──────────────────────────────────────

    /** 두 사람의 현재 약속. 아직 없으면 빈 값. */
    @Transactional(readOnly = true)
    public DatingMeetingDto meeting(Long meProfileId, Long otherProfileId) {
        long[] pair = DatingPairNote.normalize(meProfileId, otherProfileId);
        DatingPairNote note = pairNoteRepo.findByProfileAIdAndProfileBId(pair[0], pair[1]).orElse(null);
        DatingMeetingDto dto = DatingMeetingDto.from(note, meProfileId);
        return dto == null ? DatingMeetingDto.empty(otherProfileId) : dto;
    }

    /**
     * 만남 약속 제안. 서로 좋아요가 된 상대에게만 보낼 수 있다.
     *  - '날짜+시간' 후보를 최대 3개까지 고른다. 상대가 그중 하나를 고르면 확정된다.
     *  - 장소는 관리자가 정해 둔 곳으로 고정이라 손님에게 받지 않는다.
     *  - 이미 확정된 약속을 다시 제안하면 '상대 확인 대기'로 되돌아간다.
     */
    @Transactional
    public DatingMeetingDto proposeMeeting(Long meProfileId, Long otherProfileId,
                                           List<DatingMeetingDto.Slot> slots) {
        requireAcceptedPair(meProfileId, otherProfileId);
        List<DatingPairNote.Slot> picked = validSlots(slots);

        DatingPairNote note = pairNoteOf(meProfileId, otherProfileId);
        note.setPreferredSlotList(picked);
        // 후보만 정해진 상태 — 확정 시각은 상대가 고를 때 채운다.
        // 날짜는 가장 이른 후보로 적어 둬야 관리자 목록에서도 언제쯤 만나는지 보인다.
        note.setDateDay(picked.get(0).day());
        note.setDateHour(null);
        note.setPlace(meetingPlace());
        note.setProposedByProfileId(meProfileId);
        note.setAppointmentStatus(MeetingStatus.PROPOSED);
        note.setUpdatedAt(LocalDateTime.now());
        pairNoteRepo.save(note);

        notifications.notifyProfileOwner(otherProfileId, NotificationType.MEETING_PROPOSED,
                "만남 약속이 도착했어요",
                slotsText(picked) + " 중 어떠신가요? 받은 좋아요에서 하나를 골라주세요.",
                LIKES_LINK);
        return DatingMeetingDto.from(note, meProfileId);
    }

    /**
     * 상대가 제안한 약속 확정. 제안한 사람은 스스로 확정할 수 없다.
     *
     * @param day  고른 후보의 날짜
     * @param hour 고른 후보의 시각. 후보가 하나뿐이면 둘 다 비워도 그 후보로 확정한다.
     */
    @Transactional
    public DatingMeetingDto confirmMeeting(Long meProfileId, Long otherProfileId,
                                           LocalDate day, Integer hour) {
        requireAcceptedPair(meProfileId, otherProfileId);
        long[] pair = DatingPairNote.normalize(meProfileId, otherProfileId);
        DatingPairNote note = pairNoteRepo.findByProfileAIdAndProfileBId(pair[0], pair[1])
                .orElseThrow(() -> new IllegalArgumentException("아직 제안된 약속이 없어요."));
        if (note.statusOrDefault() != MeetingStatus.PROPOSED) {
            throw new IllegalStateException("확인할 약속이 없어요.");
        }
        if (meProfileId.equals(note.getProposedByProfileId())) {
            throw new IllegalStateException("상대의 확인을 기다리는 중이에요.");
        }

        List<DatingPairNote.Slot> candidates = note.preferredSlotList();
        DatingPairNote.Slot picked = null;
        if (day != null && hour != null) {
            for (DatingPairNote.Slot c : candidates) {
                if (c.day().equals(day) && c.hour() == hour) {
                    picked = c;
                    break;
                }
            }
            if (picked == null) {
                throw new IllegalArgumentException("상대가 제안한 시간 중에서 골라주세요.");
            }
        } else if (candidates.size() == 1) {
            // 고를 것이 없으면 그대로 확정한다.
            picked = candidates.get(0);
        }
        if (picked == null) {
            throw new IllegalArgumentException("만날 날짜와 시간을 골라주세요.");
        }

        note.setDateDay(picked.day());
        note.setDateHour(picked.hour());
        note.setAppointmentStatus(MeetingStatus.CONFIRMED);
        note.setUpdatedAt(LocalDateTime.now());
        pairNoteRepo.save(note);

        notifications.notifyProfileOwner(otherProfileId, NotificationType.MEETING_CONFIRMED,
                "만남 약속이 확정됐어요",
                slotText(picked) + " 에 " + meetingPlace() + "에서 만나요!", LIKES_LINK);
        return DatingMeetingDto.from(note, meProfileId);
    }

    /** 약속 화면에 함께 보여줄 안내 (장소·예약금·예약 링크). */
    @Transactional(readOnly = true)
    public MeetingGuideDto meetingGuide() {
        List<Integer> hours = new ArrayList<>();
        for (int h = DatingPairNote.MIN_HOUR; h <= DatingPairNote.MAX_HOUR; h++) hours.add(h);
        return new MeetingGuideDto(
                meetingPlace(),
                settings.get(SettingsCatalog.SELECTING_MEETING_NOTICE),
                settings.get(SettingsCatalog.SELECTING_BOOKING_NOTICE),
                emptyToNull(settings.get(SettingsCatalog.SELECTING_BOOKING_URL)),
                hours,
                DatingPairNote.MAX_PREFERRED_SLOTS);
    }

    private String meetingPlace() {
        String place = settings.get(SettingsCatalog.SELECTING_MEETING_PLACE);
        return place == null || place.isBlank() ? "담소혼술바" : place.trim();
    }

    /** 후보 검증 — 1~3개, 지난 날짜 제외, 영업 시간대 안, 중복 제거 후 이른 순. */
    private static List<DatingPairNote.Slot> validSlots(List<DatingMeetingDto.Slot> slots) {
        List<DatingPairNote.Slot> picked = new ArrayList<>();
        LocalDate today = LocalDate.now();
        if (slots != null) {
            for (DatingMeetingDto.Slot s : slots) {
                if (s == null || s.day() == null || s.hour() == null) continue;
                if (s.day().isBefore(today)) {
                    throw new IllegalArgumentException("지난 날짜로는 약속을 잡을 수 없어요.");
                }
                if (s.hour() < DatingPairNote.MIN_HOUR || s.hour() > DatingPairNote.MAX_HOUR) {
                    throw new IllegalArgumentException(
                            "만나는 시간은 " + DatingPairNote.MIN_HOUR + "시부터 "
                                    + DatingPairNote.MAX_HOUR + "시까지만 고를 수 있어요.");
                }
                DatingPairNote.Slot slot = new DatingPairNote.Slot(s.day(), s.hour());
                if (!picked.contains(slot)) picked.add(slot);
            }
        }
        if (picked.isEmpty()) {
            throw new IllegalArgumentException("만날 날짜와 시간을 하나 이상 골라주세요.");
        }
        if (picked.size() > DatingPairNote.MAX_PREFERRED_SLOTS) {
            throw new IllegalArgumentException(
                    "후보는 최대 " + DatingPairNote.MAX_PREFERRED_SLOTS + "개까지 고를 수 있어요.");
        }
        picked.sort(Comparator.comparing(DatingPairNote.Slot::day)
                .thenComparingInt(DatingPairNote.Slot::hour));
        return picked;
    }

    /** 2026-09-05 19시 → "9/5 19:00" */
    private static String slotText(DatingPairNote.Slot slot) {
        return slot.day().getMonthValue() + "/" + slot.day().getDayOfMonth()
                + " " + String.format("%02d:00", slot.hour());
    }

    /** 후보 여럿을 "9/5 19:00 · 9/6 20:00" 으로. */
    private static String slotsText(List<DatingPairNote.Slot> slots) {
        return slots.stream().map(DatingService::slotText).collect(Collectors.joining(" · "));
    }

    /** 약속 취소 — 잡힌 날짜·시간을 비우고 상대에게 알린다. (관리자 메모는 남긴다) */
    @Transactional
    public DatingMeetingDto cancelMeeting(Long meProfileId, Long otherProfileId) {
        requireAcceptedPair(meProfileId, otherProfileId);
        long[] pair = DatingPairNote.normalize(meProfileId, otherProfileId);
        DatingPairNote note = pairNoteRepo.findByProfileAIdAndProfileBId(pair[0], pair[1]).orElse(null);
        if (note == null || note.statusOrDefault() == MeetingStatus.NONE) {
            return DatingMeetingDto.empty(otherProfileId);
        }
        note.setDateDay(null);
        note.setDateHour(null);
        note.setPreferredSlotList(null);
        note.setPlace(null);
        note.setProposedByProfileId(null);
        note.setAppointmentStatus(MeetingStatus.NONE);
        note.setUpdatedAt(LocalDateTime.now());
        pairNoteRepo.save(note);

        notifications.notifyProfileOwner(otherProfileId, NotificationType.MEETING_CANCELED,
                "만남 약속이 취소됐어요",
                "다른 날짜로 다시 제안해보세요.", LIKES_LINK);
        return DatingMeetingDto.from(note, meProfileId);
    }

    /** 서로 좋아요가 된 사이인지 확인. 아니면 약속 기능을 쓸 수 없다. */
    private void requireAcceptedPair(Long meProfileId, Long otherProfileId) {
        if (meProfileId.equals(otherProfileId)) {
            throw new IllegalArgumentException("서로 다른 두 사람이어야 해요.");
        }
        get(meProfileId);
        get(otherProfileId);
        boolean accepted = likeRepo.findByFromProfileIdAndToProfileId(meProfileId, otherProfileId)
                        .map(DatingLike::isAccepted).orElse(false)
                || likeRepo.findByFromProfileIdAndToProfileId(otherProfileId, meProfileId)
                        .map(DatingLike::isAccepted).orElse(false);
        if (!accepted) {
            throw new IllegalStateException("서로 좋아요가 된 뒤에 약속을 잡을 수 있어요.");
        }
    }

    /** 페어 기록을 가져오거나 새로 만든다. (두 id 는 순서를 가리지 않는다) */
    private DatingPairNote pairNoteOf(Long xId, Long yId) {
        long[] pair = DatingPairNote.normalize(xId, yId);
        return pairNoteRepo.findByProfileAIdAndProfileBId(pair[0], pair[1])
                .orElseGet(() -> DatingPairNote.builder()
                        .profileAId(pair[0]).profileBId(pair[1]).build());
    }

    // ─── 관리자: 조회 ────────────────────────────────────
    /**
     * 관리자 목록.
     * @param includeDeleted true 면 소프트 삭제된 회원까지 포함(휴지통 탭용)
     */
    @Transactional(readOnly = true)
    public List<DatingAdminDto> listAllForAdmin(boolean includeDeleted) {
        List<DatingProfile> all = new ArrayList<>(repo.findAll());
        if (!includeDeleted) all.removeIf(p -> p.getDeletedAt() != null);
        all.sort(Comparator.comparing(DatingProfile::getCreatedAt).reversed());

        Map<Long, String> nameById = new HashMap<>();
        repo.findAll().forEach(p -> nameById.put(p.getId(), p.getName()));

        List<DatingQuestion> questions = questionRepo.findAllByOrderBySortOrderAsc();
        Map<Long, Map<Long, String>> answers = answersByProfile();

        Map<Long, Set<Long>> given = new HashMap<>();
        Map<Long, Set<Long>> received = new HashMap<>();
        for (DatingLike l : likeRepo.findAll()) {
            given.computeIfAbsent(l.getFromProfileId(), k -> new LinkedHashSet<>()).add(l.getToProfileId());
            received.computeIfAbsent(l.getToProfileId(), k -> new LinkedHashSet<>()).add(l.getFromProfileId());
        }

        List<DatingAdminDto> out = new ArrayList<>();
        for (DatingProfile p : all) {
            Set<Long> g = given.getOrDefault(p.getId(), Set.of());
            Set<Long> r = received.getOrDefault(p.getId(), Set.of());
            Set<Long> mutual = new LinkedHashSet<>(g);
            mutual.retainAll(r);
            out.add(new DatingAdminDto(
                    p.getId(), p.getName(), p.getContact(), p.getBirthYear(), p.getGender(),
                    p.getPhotoUrl(), p.getCreatedAt(),
                    answerViews(p.getId(), questions, answers),
                    namesOf(g, nameById), namesOf(r, nameById), namesOf(mutual, nameById),
                    DatingStatus.orDefault(p.getStatus()), p.getAdminMemo(), p.getDeletedAt(),
                    p.getVerifiedAt(), p.getHiddenAt()
            ));
        }
        return out;
    }

    /**
     * 관리자 '선택 내역' — 누가 누구를 언제 선택했는지. 최신순.
     *  - 프로필이 완전 삭제된 기록은 표시할 이름이 없으므로 건너뛴다.
     *  - 쌍방은 같은 페어가 두 줄이 되므로 <b>여자 → 남자</b> 한 줄만 남긴다.
     *    지워지는 남자 → 여자 줄의 선택 시각은 partnerAt 으로 보존한다.
     */
    @Transactional(readOnly = true)
    public List<DatingLikeDto> listLikesForAdmin() {
        Map<Long, DatingProfile> byId = new HashMap<>();
        repo.findAll().forEach(p -> byId.put(p.getId(), p));

        Map<String, DatingPairNote> notes = new HashMap<>();
        for (DatingPairNote n : pairNoteRepo.findAll()) {
            notes.put(n.getProfileAId() + "-" + n.getProfileBId(), n);
        }

        List<DatingLike> likes = likeRepo.findAll();
        Map<String, LocalDateTime> when = new HashMap<>();
        for (DatingLike l : likes) {
            when.put(l.getFromProfileId() + ">" + l.getToProfileId(), l.getCreatedAt());
        }

        List<DatingLikeDto> out = new ArrayList<>();
        for (DatingLike l : likes) {
            DatingProfile from = byId.get(l.getFromProfileId());
            DatingProfile to = byId.get(l.getToProfileId());
            if (from == null || to == null) continue;

            LocalDateTime back = when.get(l.getToProfileId() + ">" + l.getFromProfileId());
            boolean mutual = back != null;
            // 쌍방이면 여자 → 남자 줄만 남긴다 (남 → 여 줄은 중복이라 버림)
            if (mutual && from.getGender() == Gender.MALE && to.getGender() == Gender.FEMALE) {
                continue;
            }
            // 쌍방이 성립한 시각 = 두 선택 중 나중에 누른 쪽
            LocalDateTime mutualAt = !mutual ? null
                    : (back.isAfter(l.getCreatedAt()) ? back : l.getCreatedAt());

            long[] pair = DatingPairNote.normalize(l.getFromProfileId(), l.getToProfileId());
            DatingPairNote note = notes.get(pair[0] + "-" + pair[1]);

            out.add(new DatingLikeDto(
                    l.getId(),
                    from.getId(), from.getName(), from.getGender(), from.getBirthYear(),
                    from.getDeletedAt() != null,
                    to.getId(), to.getName(), to.getGender(), to.getBirthYear(),
                    to.getDeletedAt() != null,
                    l.getCreatedAt(), mutual, mutualAt, back,
                    note == null ? null : note.getMemo(),
                    note == null ? null : note.getDateDay(),
                    note == null ? null : note.getDateHour(),
                    l.statusOrDefault(), l.statusOrDefault().label()));
        }
        out.sort(Comparator.comparing(DatingLikeDto::createdAt).reversed());
        return out;
    }

    /**
     * 페어 메모 / 데이트 약속 전체. 약속이 잡힌 것부터(날짜·시간 순), 미정은 뒤로.
     * 회원이 완전 삭제된 기록은 건너뛴다.
     */
    @Transactional(readOnly = true)
    public List<DatingPairNoteDto> listPairNotes() {
        Map<Long, DatingProfile> byId = new HashMap<>();
        repo.findAll().forEach(p -> byId.put(p.getId(), p));

        List<DatingPairNoteDto> out = new ArrayList<>();
        for (DatingPairNote n : pairNoteRepo.findAll()) {
            DatingProfile a = byId.get(n.getProfileAId());
            DatingProfile b = byId.get(n.getProfileBId());
            if (a == null || b == null) continue;
            out.add(new DatingPairNoteDto(
                    n.getId(), a.getId(), a.getName(), b.getId(), b.getName(),
                    n.getMemo(), n.getDateDay(), n.getDateHour(), n.getUpdatedAt(),
                    n.getPlace(), n.statusOrDefault()));
        }
        out.sort(Comparator
                .comparing(DatingPairNoteDto::dateDay, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(DatingPairNoteDto::dateHour, Comparator.nullsLast(Comparator.naturalOrder())));
        return out;
    }

    /**
     * 페어 메모 / 데이트 약속 저장 (없으면 생성). 두 id 는 순서를 가리지 않는다.
     * 메모·날짜·시간 모두 비면 기록을 지운다.
     */
    @Transactional
    public void savePairNote(Long xId, Long yId, String memo, LocalDate dateDay, Integer dateHour) {
        if (xId == null || yId == null || xId.equals(yId)) {
            throw new IllegalArgumentException("서로 다른 두 회원을 지정해주세요.");
        }
        getRaw(xId);
        getRaw(yId);
        if (dateHour != null
                && (dateHour < DatingPairNote.MIN_HOUR || dateHour > DatingPairNote.MAX_HOUR)) {
            throw new IllegalArgumentException(
                    "데이트 시간은 " + DatingPairNote.MIN_HOUR + "시부터 "
                            + DatingPairNote.MAX_HOUR + "시까지만 선택할 수 있습니다.");
        }

        long[] pair = DatingPairNote.normalize(xId, yId);
        DatingPairNote note = pairNoteRepo
                .findByProfileAIdAndProfileBId(pair[0], pair[1])
                .orElse(null);

        String cleanMemo = emptyToNull(memo);
        boolean guestMeeting = note != null && note.statusOrDefault() != MeetingStatus.NONE;
        if (cleanMemo == null && dateDay == null && dateHour == null && !guestMeeting) {
            if (note != null) pairNoteRepo.delete(note);
            return;
        }

        if (note == null) {
            note = DatingPairNote.builder()
                    .profileAId(pair[0]).profileBId(pair[1]).build();
        }
        note.setMemo(cleanMemo);
        note.setDateDay(dateDay);
        note.setDateHour(dateHour);
        // 관리자가 날짜와 시간을 다 잡아줬으면 확정으로 본다. 손님이 고르던 후보는 정리한다.
        if (dateDay != null && dateHour != null) {
            note.setAppointmentStatus(MeetingStatus.CONFIRMED);
            note.setPreferredSlotList(null);
        } else if (dateDay != null && note.statusOrDefault() == MeetingStatus.NONE) {
            note.setAppointmentStatus(MeetingStatus.CONFIRMED);
        } else if (dateDay == null && note.statusOrDefault() == MeetingStatus.CONFIRMED) {
            note.setAppointmentStatus(MeetingStatus.NONE);
            note.setProposedByProfileId(null);
        }
        note.setUpdatedAt(LocalDateTime.now());
        pairNoteRepo.save(note);
    }

    /** 현재 쌍방 찜인 페어 (정규화된 [작은 id, 큰 id]). */
    @Transactional(readOnly = true)
    public Set<List<Long>> mutualPairs() {
        Set<String> edges = new HashSet<>();
        List<DatingLike> likes = likeRepo.findAll();
        for (DatingLike l : likes) edges.add(l.getFromProfileId() + ">" + l.getToProfileId());

        Set<List<Long>> pairs = new LinkedHashSet<>();
        for (DatingLike l : likes) {
            if (!edges.contains(l.getToProfileId() + ">" + l.getFromProfileId())) continue;
            long a = Math.min(l.getFromProfileId(), l.getToProfileId());
            long b = Math.max(l.getFromProfileId(), l.getToProfileId());
            pairs.add(List.of(a, b));
        }
        return pairs;
    }

    // ─── 본인 수정 ──────────────────────────────────────
    /** 본인이 작성한 정보(기본 정보 + 설문 + 사진) 수정. 성별은 바꿀 수 없다. */
    @Transactional
    public void selfUpdate(Long id, DatingSelfUpdateRequest req) {
        DatingProfile p = get(id); // 삭제된 프로필이면 예외
        update(id, new ProfileUpdate(
                req.name(), req.contact(), req.birthYear(),
                p.getGender(), req.photoUrl(), req.answers()));
    }

    /** 본인 설문 답변 (수정 화면 프리필용). 값이 빈 답변은 빠진다. */
    @Transactional(readOnly = true)
    public List<DatingAnswerView> answersOf(Long profileId) {
        Map<Long, String> mine = new HashMap<>();
        for (DatingAnswer a : answerRepo.findByProfileId(profileId)) {
            mine.put(a.getQuestionId(), a.getValue());
        }
        return answerViews(profileId, questionRepo.findAllByOrderBySortOrderAsc(),
                Map.of(profileId, mine));
    }

    // ─── 관리자: 수정 ────────────────────────────────────
    @Transactional
    public void update(Long id, ProfileUpdate req) {
        DatingProfile p = getRaw(id);
        List<DatingQuestion> questions = questionRepo.findAllByOrderBySortOrderAsc();
        Map<Long, String> answers = validatedAnswers(req.answers(), questions);

        p.setName(req.name().trim());
        p.setContact(req.contact().trim());
        p.setBirthYear(req.birthYear());
        p.setGender(req.gender());
        p.setPhotoUrl(emptyToNull(req.photoUrl()));
        repo.save(p);

        // 답변 전체 교체
        answerRepo.deleteByProfileId(id);
        saveAnswers(id, answers, questions);
    }

    @Transactional
    public void setStatus(Long id, DatingStatus status) {
        DatingProfile p = getRaw(id);
        p.setStatus(status == null ? DatingStatus.PENDING : status);
        repo.save(p);
    }

    @Transactional
    public void setMemo(Long id, String memo) {
        DatingProfile p = getRaw(id);
        p.setAdminMemo(emptyToNull(memo));
        repo.save(p);
    }

    // ─── 관리자: 삭제 / 복구 ─────────────────────────────
    /** 소프트 삭제 — 목록에서 숨기지만 찜/답변 기록은 보존한다. 상태도 제외로 바꾼다. */
    @Transactional
    public void softDelete(Long id) {
        DatingProfile p = getRaw(id);
        if (p.getDeletedAt() == null) {
            p.setDeletedAt(LocalDateTime.now());
            p.setStatus(DatingStatus.EXCLUDED);
            repo.save(p);
        }
    }

    @Transactional
    public void restore(Long id) {
        DatingProfile p = getRaw(id);
        p.setDeletedAt(null);
        repo.save(p);
    }

    /** 완전 삭제 — 찜·답변·매칭 이력까지 지운다. 복구 불가. */
    @Transactional
    public void hardDelete(Long id) {
        if (!repo.existsById(id)) throw new IllegalArgumentException("프로필을 찾을 수 없습니다.");
        likeRepo.deleteByFromProfileIdOrToProfileId(id, id);
        passRepo.deleteByFromProfileIdOrToProfileId(id, id);
        answerRepo.deleteByProfileId(id);
        matchRepo.deleteByProfileAIdOrProfileBId(id, id);
        repo.deleteById(id);
    }

    // ─── 관리자: 일괄 처리 ───────────────────────────────
    @Transactional
    public int bulkStatus(List<Long> ids, DatingStatus status) {
        int n = 0;
        for (Long id : safeIds(ids)) {
            Optional<DatingProfile> found = repo.findById(id);
            if (found.isEmpty()) continue;
            DatingProfile p = found.get();
            p.setStatus(status == null ? DatingStatus.PENDING : status);
            repo.save(p);
            n++;
        }
        return n;
    }

    @Transactional
    public int bulkSoftDelete(List<Long> ids) {
        LocalDateTime now = LocalDateTime.now();
        int n = 0;
        for (Long id : safeIds(ids)) {
            Optional<DatingProfile> found = repo.findById(id);
            if (found.isEmpty()) continue;
            DatingProfile p = found.get();
            if (p.getDeletedAt() == null) {
                p.setDeletedAt(now);
                p.setStatus(DatingStatus.EXCLUDED);
                repo.save(p);
            }
            n++;
        }
        return n;
    }

    @Transactional
    public int bulkRestore(List<Long> ids) {
        int n = 0;
        for (Long id : safeIds(ids)) {
            Optional<DatingProfile> found = repo.findById(id);
            if (found.isEmpty()) continue;
            DatingProfile p = found.get();
            p.setDeletedAt(null);
            repo.save(p);
            n++;
        }
        return n;
    }

    @Transactional
    public int bulkHardDelete(List<Long> ids) {
        int n = 0;
        for (Long id : safeIds(ids)) {
            if (!repo.existsById(id)) continue;
            hardDelete(id);
            n++;
        }
        return n;
    }

    // ─── 헬퍼 ───────────────────────────────────────────
    /** 요청 답변을 (questionId -> 값) 으로 정리하고 필수 질문 누락을 검증한다. */
    private Map<Long, String> validatedAnswers(List<AnswerInput> input, List<DatingQuestion> questions) {
        Map<Long, String> answers = new HashMap<>();
        if (input != null) {
            for (AnswerInput a : input) {
                if (a == null || a.questionId() == null) continue;
                answers.put(a.questionId(), a.value() == null ? "" : a.value().trim());
            }
        }
        for (DatingQuestion q : questions) {
            if (!q.isRequired()) continue;
            String v = answers.get(q.getId());
            if (v == null || v.isBlank()) {
                throw new IllegalArgumentException("'" + q.getLabel() + "'을(를) 입력해주세요.");
            }
        }
        return answers;
    }

    /** 알려진 질문에 대한 답변만 저장 (빈 값 제외). */
    private void saveAnswers(Long profileId, Map<Long, String> answers, List<DatingQuestion> questions) {
        Set<Long> known = questions.stream().map(DatingQuestion::getId).collect(Collectors.toSet());
        for (Map.Entry<Long, String> e : answers.entrySet()) {
            if (!known.contains(e.getKey()) || e.getValue().isBlank()) continue;
            answerRepo.save(DatingAnswer.builder()
                    .profileId(profileId).questionId(e.getKey()).value(e.getValue()).build());
        }
    }

    /** 전체 답변을 profileId -> (questionId -> value) 로 묶는다. */
    private Map<Long, Map<Long, String>> answersByProfile() {
        Map<Long, Map<Long, String>> m = new HashMap<>();
        for (DatingAnswer a : answerRepo.findAll()) {
            m.computeIfAbsent(a.getProfileId(), k -> new HashMap<>()).put(a.getQuestionId(), a.getValue());
        }
        return m;
    }

    /** 질문 순서대로, 값이 있는 답변만 뷰로 변환. */
    private List<DatingAnswerView> answerViews(Long profileId, List<DatingQuestion> questions,
                                               Map<Long, Map<Long, String>> answers) {
        Map<Long, String> mine = answers.getOrDefault(profileId, Map.of());
        List<DatingAnswerView> out = new ArrayList<>();
        for (DatingQuestion q : questions) {
            String v = mine.get(q.getId());
            if (v == null || v.isBlank()) continue;
            out.add(new DatingAnswerView(q.getId(), q.getLabel(), v));
        }
        return out;
    }

    private static List<String> namesOf(Set<Long> ids, Map<Long, String> nameById) {
        return ids.stream().map(id -> nameById.getOrDefault(id, "?")).toList();
    }

    private static List<Long> safeIds(List<Long> ids) {
        if (ids == null) return List.of();
        return ids.stream().filter(Objects::nonNull).distinct().toList();
    }

    private static String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}