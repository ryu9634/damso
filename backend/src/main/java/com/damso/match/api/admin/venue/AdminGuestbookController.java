package com.damso.match.api.admin.venue;

import com.damso.match.domain.venue.dto.GuestbookDto;
import com.damso.match.domain.venue.service.GuestbookService;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 관리자 — 방명록 관리 (숨기기 · 삭제).
 *
 * <p>URL 접두어가 {@code /api/admin/app/guestbook} 인 것은 손님 앱의 방명록이라는 뜻이다.
 * 화면이 이 주소를 쓰고 있어 그대로 두고, 클래스만 방명록이 속한 venue 로 옮겼다.
 */
@RestController
@RequestMapping("/api/admin/app/guestbook")
@RequiredArgsConstructor
public class AdminGuestbookController {

    private final GuestbookService guestbookService;

    @GetMapping
    public List<GuestbookDto.AdminView> guestbook() {
        return guestbookService.listForAdmin();
    }

    @PatchMapping("/{id}/hidden")
    public GuestbookDto.AdminView setHidden(@PathVariable Long id, @RequestBody HiddenRequest req) {
        return guestbookService.setHidden(id, Boolean.TRUE.equals(req.hidden()));
    }

    @DeleteMapping("/{id}")
    public void deleteEntry(@PathVariable Long id) {
        guestbookService.delete(id);
    }

    public record HiddenRequest(Boolean hidden) {}
}
