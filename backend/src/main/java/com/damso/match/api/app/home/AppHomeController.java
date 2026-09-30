package com.damso.match.api.app.home;

import com.damso.match.api.app.home.dto.AppFeatureDto;
import com.damso.match.api.app.home.dto.AppHomeDto;
import com.damso.match.api.app.home.dto.AppWeekDto;
import com.damso.match.domain.member.entity.AppUser;
import com.damso.match.domain.member.service.AuthService;
import com.damso.match.domain.setting.service.FeatureService;
import com.damso.match.domain.venue.dto.GuestbookDto;
import com.damso.match.domain.venue.service.CalendarService;
import com.damso.match.domain.venue.service.GuestbookService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 앱(손님) 홈 · 방명록 API.
 *  - 홈/방명록 열람은 로그인 없이도 가능하고, 방명록 작성만 로그인이 필요하다.
 */
@RestController
@RequestMapping("/api/app")
@RequiredArgsConstructor
public class AppHomeController {

    private final AppHomeService homeService;
    private final GuestbookService guestbookService;
    private final AuthService authService;
    private final FeatureService featureService;
    private final CalendarService calendarService;

    /** 홈. 로그인돼 있으면 진행 중인 소개팅에 이미 참여했는지까지 함께 내려준다. */
    @GetMapping("/home")
    public AppHomeDto home(@RequestHeader(value = "Authorization", required = false) String auth) {
        return homeService.home(userIdOrNull(auth));
    }

    /** 토큰이 없거나 만료됐어도 홈은 열려야 한다. 그때는 회원 정보 없이 그린다. */
    private Long userIdOrNull(String auth) {
        if (auth == null || auth.isBlank()) return null;
        try {
            return authService.requireUser(auth).getId();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * 지금 쓸 수 있는 기능 (담소 소개팅 · 담소 셀렉팅).
     * 앱은 이 값으로 하단 탭을 감추고, 꺼진 화면에는 안내 문구를 대신 보여준다.
     */
    @GetMapping("/features")
    public AppFeatureDto features() {
        return featureService.forApp();
    }

    /**
     * 이번 주(월~일) 캘린더 — 가게 행사·휴일 + 본인의 만남 약속.
     * 로그인하지 않아도 가게 일정은 볼 수 있고, 약속은 본인 것만 담긴다.
     */
    @GetMapping("/calendar/week")
    public AppWeekDto week(@RequestHeader(value = "Authorization", required = false) String auth) {
        return calendarService.week(authService.currentUser(auth));
    }

    @GetMapping("/guestbook")
    public List<GuestbookDto> guestbook(@RequestHeader(value = "Authorization", required = false) String auth) {
        return guestbookService.listForApp(authService.currentUser(auth));
    }

    @PostMapping("/guestbook")
    public GuestbookDto write(@RequestHeader(value = "Authorization", required = false) String auth,
                              @Valid @RequestBody GuestbookDto.WriteRequest req) {
        AppUser user = authService.requireUser(auth);
        return guestbookService.write(user, req);
    }

    @DeleteMapping("/guestbook/{id}")
    public void deleteOwn(@RequestHeader(value = "Authorization", required = false) String auth,
                          @PathVariable Long id) {
        AppUser user = authService.requireUser(auth);
        guestbookService.deleteOwn(user, id);
    }
}
