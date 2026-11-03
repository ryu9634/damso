package com.damso.match.api.admin.dating;

import com.damso.match.api.admin.dating.dto.DatingAdminDto;
import com.damso.match.api.admin.dating.dto.DatingLikeDto;
import com.damso.match.api.admin.dating.dto.DatingPairNoteDto;
import com.damso.match.domain.dating.service.DatingService;
import com.damso.match.api.admin.dating.dto.DatingAdminRequests.*;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 관리자: 소개팅 회원 관리.
 *  - 조회(찜/쌍방 현황 포함) · 수정 · 상태/메모 · 소프트 삭제/복구/완전삭제 · 일괄 처리
 */
@RestController
@RequestMapping("/api/admin/dating")
@RequiredArgsConstructor
public class AdminDatingController {

    private final DatingService datingService;

    // ─── 프로필 ─────────────────────────────────────────
    /** @param includeDeleted true 면 삭제된 회원(휴지통)까지 함께 내려준다. */
    @GetMapping("/profiles")
    public List<DatingAdminDto> list(
            @RequestParam(defaultValue = "false") boolean includeDeleted) {
        return datingService.listAllForAdmin(includeDeleted);
    }

    @PutMapping("/profiles/{id}")
    public void update(@PathVariable Long id, @Valid @RequestBody ProfileUpdate req) {
        datingService.update(id, req);
    }

    @PatchMapping("/profiles/{id}/status")
    public void setStatus(@PathVariable Long id, @Valid @RequestBody StatusChange req) {
        datingService.setStatus(id, req.status());
    }

    @PatchMapping("/profiles/{id}/memo")
    public void setMemo(@PathVariable Long id, @Valid @RequestBody MemoChange req) {
        datingService.setMemo(id, req.memo());
    }

    /**
     * 매장 대면 확인 배지 on/off.
     * 실제로 매장에서 얼굴을 본 회원에게만 켠다 — 손님이 상대를 믿을 수 있는 유일한 근거다.
     */
    @PatchMapping("/profiles/{id}/verify")
    public void setVerified(@PathVariable Long id, @RequestBody VerifyChange req) {
        datingService.setVerified(id, Boolean.TRUE.equals(req.verified()));
    }

    public record VerifyChange(Boolean verified) {}

    /** 소프트 삭제 (휴지통으로 이동). */
    @DeleteMapping("/profiles/{id}")
    public void delete(@PathVariable Long id) {
        datingService.softDelete(id);
    }

    @PostMapping("/profiles/{id}/restore")
    public void restore(@PathVariable Long id) {
        datingService.restore(id);
    }

    /** 완전 삭제 — 찜·답변·매칭 이력까지 제거. 복구 불가. */
    @DeleteMapping("/profiles/{id}/hard")
    public void hardDelete(@PathVariable Long id) {
        datingService.hardDelete(id);
    }

    // ─── 일괄 처리 ──────────────────────────────────────
    @PostMapping("/profiles/bulk/status")
    public Map<String, Integer> bulkStatus(@Valid @RequestBody Bulk req) {
        return Map.of("updated", datingService.bulkStatus(req.ids(), req.status()));
    }

    @PostMapping("/profiles/bulk/delete")
    public Map<String, Integer> bulkDelete(@Valid @RequestBody Bulk req) {
        return Map.of("updated", datingService.bulkSoftDelete(req.ids()));
    }

    @PostMapping("/profiles/bulk/restore")
    public Map<String, Integer> bulkRestore(@Valid @RequestBody Bulk req) {
        return Map.of("updated", datingService.bulkRestore(req.ids()));
    }

    @PostMapping("/profiles/bulk/hard-delete")
    public Map<String, Integer> bulkHardDelete(@Valid @RequestBody Bulk req) {
        return Map.of("updated", datingService.bulkHardDelete(req.ids()));
    }

    // ─── 선택(찜) 내역 ──────────────────────────────────
    /** 누가 누구를 언제 선택했는지 전체 목록 (최신순). */
    @GetMapping("/likes")
    public List<DatingLikeDto> likes() {
        return datingService.listLikesForAdmin();
    }

    /** 페어 메모 + 데이트 약속 전체 (약속 캘린더용). */
    @GetMapping("/pairs/notes")
    public List<DatingPairNoteDto> pairNotes() {
        return datingService.listPairNotes();
    }

    /**
     * 페어(두 회원) 메모 + 데이트 약속 저장. 두 id 순서는 상관없다.
     * 메모·날짜·시간을 모두 비우면 기록이 삭제된다.
     */
    @PutMapping("/pairs/{aId}/{bId}/note")
    public void savePairNote(@PathVariable Long aId, @PathVariable Long bId,
                             @Valid @RequestBody PairNoteSave req) {
        datingService.savePairNote(aId, bId, req.memo(), req.dateDay(), req.dateHour());
    }
}