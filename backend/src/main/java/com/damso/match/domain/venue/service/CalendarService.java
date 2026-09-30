package com.damso.match.domain.venue.service;

import com.damso.match.api.admin.venue.dto.CalendarEntryDto;
import com.damso.match.api.app.home.dto.AppWeekDto;
import com.damso.match.domain.dating.entity.DatingPairNote;
import com.damso.match.domain.dating.entity.DatingProfile;
import com.damso.match.domain.dating.entity.MeetingStatus;
import com.damso.match.domain.dating.repository.DatingPairNoteRepository;
import com.damso.match.domain.dating.repository.DatingProfileRepository;
import com.damso.match.domain.member.entity.AppUser;
import com.damso.match.domain.setting.service.FeatureService;
import com.damso.match.domain.venue.entity.CalendarEntry;
import com.damso.match.domain.venue.entity.CalendarEntryType;
import com.damso.match.domain.venue.repository.CalendarEntryRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 가게 캘린더 (행사 · 휴일) + 앱 홈의 '이번 주' 보기.
 *  - 관리자가 등록한 일정은 모두에게 같이 보인다.
 *  - 담소 셀렉팅에서 잡힌 만남 약속은 그 약속의 당사자에게만 보인다.
 *    (상대가 누구인지까지는 여기서 밝히지 않고 '받은 좋아요' 화면에서 확인하게 둔다)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CalendarService {

    private final CalendarEntryRepository repo;
    private final DatingProfileRepository profileRepo;
    private final DatingPairNoteRepository pairNoteRepo;
    private final FeatureService featureService;

    // ─── 관리자 ─────────────────────────────────────────

    /** 전체 일정 (날짜순). */
    public List<CalendarEntryDto> listAll() {
        return repo.findAllByOrderByStartDayAscIdAsc().stream().map(CalendarEntryDto::from).toList();
    }

    @Transactional
    public CalendarEntryDto create(CalendarEntryDto.SaveRequest req) {
        CalendarEntry c = new CalendarEntry();
        apply(c, req);
        return CalendarEntryDto.from(repo.save(c));
    }

    @Transactional
    public CalendarEntryDto update(Long id, CalendarEntryDto.SaveRequest req) {
        CalendarEntry c = get(id);
        apply(c, req);
        return CalendarEntryDto.from(repo.save(c));
    }

    @Transactional
    public void delete(Long id) {
        repo.delete(get(id));
    }

    public CalendarEntry get(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("일정을 찾을 수 없습니다."));
    }

    // ─── 앱: 이번 주 ────────────────────────────────────

    /**
     * 오늘이 낀 주(월~일)의 일정.
     *
     * @param user 로그인한 회원. null 이면 가게 일정만 담는다.
     */
    public AppWeekDto week(AppUser user) {
        LocalDate today = LocalDate.now();
        LocalDate start = today.with(DayOfWeek.MONDAY);
        LocalDate end = start.plusDays(6);

        List<CalendarEntry> entries = repo.findOverlapping(start, end);
        List<Meeting> meetings = meetingsOf(user, start, end);

        List<AppWeekDto.Day> days = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate day = start.plusDays(i);
            List<AppWeekDto.Item> items = new ArrayList<>();

            for (CalendarEntry c : entries) {
                if (!c.covers(day)) continue;
                CalendarEntryType type = CalendarEntryType.orDefault(c.getType());
                items.add(new AppWeekDto.Item(
                        type == CalendarEntryType.HOLIDAY ? AppWeekDto.Kind.HOLIDAY : AppWeekDto.Kind.EVENT,
                        c.getTitle(),
                        detailOf(c),
                        c.emojiOrDefault()));
            }
            for (Meeting m : meetings) {
                if (!m.day().equals(day)) continue;
                items.add(new AppWeekDto.Item(
                        AppWeekDto.Kind.MEETING, "만남 약속", m.detail(), "💗"));
            }

            days.add(new AppWeekDto.Day(day, day.getDayOfWeek().getValue(), day.equals(today), items));
        }
        return new AppWeekDto(start, end, days);
    }

    /** 이 회원이 잡아둔 만남 약속 중 이번 주에 걸린 것. */
    private List<Meeting> meetingsOf(AppUser user, LocalDate start, LocalDate end) {
        // 셀렉팅을 꺼둔 동안에는 약속도 홈에 띄우지 않는다. (기능 자체가 잠긴 상태)
        if (user == null || !featureService.selectingEnabled()) return List.of();

        DatingProfile me = profileRepo.findByAppUserIdAndDeletedAtIsNull(user.getId()).orElse(null);
        if (me == null) return List.of();

        List<Meeting> out = new ArrayList<>();
        for (DatingPairNote n : pairNoteRepo.findByProfileAIdOrProfileBId(me.getId(), me.getId())) {
            if (n.statusOrDefault() == MeetingStatus.NONE) continue;

            if (n.statusOrDefault() == MeetingStatus.PROPOSED) {
                // 아직 고르는 중 — 후보 날짜를 저마다 표시한다. 어느 날이 될지 모르니 다 걸어 둔다.
                for (DatingPairNote.Slot slot : n.preferredSlotList()) {
                    if (slot.day().isBefore(start) || slot.day().isAfter(end)) continue;
                    out.add(new Meeting(slot.day(),
                            detailOf(String.format("%02d:00", slot.hour()), n.getPlace(), true)));
                }
                continue;
            }

            if (n.getDateDay() == null) continue;
            if (n.getDateDay().isBefore(start) || n.getDateDay().isAfter(end)) continue;
            String time = n.getDateHour() == null ? "" : String.format("%02d:00", n.getDateHour());
            out.add(new Meeting(n.getDateDay(), detailOf(time, n.getPlace(), false)));
        }
        return out;
    }

    /** "19:00 · 담소혼술바 · 시간 조율 중" 처럼 한 줄로 잇는다. */
    private static String detailOf(String time, String place, boolean deciding) {
        List<String> parts = new ArrayList<>();
        if (time != null && !time.isBlank()) parts.add(time);
        if (place != null && !place.isBlank()) parts.add(place);
        if (deciding) parts.add("시간 조율 중");
        return String.join(" · ", parts);
    }

    // ─── 공통 ───────────────────────────────────────────

    private void apply(CalendarEntry c, CalendarEntryDto.SaveRequest req) {
        LocalDate end = req.endDay();
        if (end != null && end.isBefore(req.startDay())) {
            throw new IllegalArgumentException("끝나는 날이 시작일보다 빠를 수 없어요.");
        }
        c.setType(CalendarEntryType.orDefault(req.type()));
        c.setTitle(req.title().trim());
        c.setDescription(emptyToNull(req.description()));
        c.setStartDay(req.startDay());
        // 하루짜리면 굳이 끝나는 날을 저장하지 않는다.
        c.setEndDay(end != null && end.equals(req.startDay()) ? null : end);
        c.setTimeText(emptyToNull(req.timeText()));
        c.setEmoji(emptyToNull(req.emoji()));
        c.setUpdatedAt(LocalDateTime.now());
    }

    /** 홈에 한 줄로 보여줄 보조 문구 — 시간 문구가 있으면 그것, 없으면 설명. */
    private static String detailOf(CalendarEntry c) {
        if (c.getTimeText() != null && !c.getTimeText().isBlank()) return c.getTimeText().trim();
        return c.getDescription();
    }

    private static String emptyToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /** 홈에 띄울 만남 약속 한 건. */
    private record Meeting(LocalDate day, String detail) {}
}
