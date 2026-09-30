package com.damso.match.api.admin.game;

import com.damso.match.api.admin.game.dto.GameContentDto;
import com.damso.match.domain.game.dto.GameRoomDto;
import com.damso.match.domain.game.service.GameService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 관리자 — 게임 문항 관리와 열린 방 확인. */
@RestController
@RequestMapping("/api/admin/games")
@RequiredArgsConstructor
public class AdminGameController {

    private final GameService gameService;

    // ─── 문항 ───────────────────────────────────────────

    @GetMapping("/contents")
    public List<GameContentDto> contents() {
        return gameService.listContents();
    }

    @PostMapping("/contents")
    public GameContentDto create(@Valid @RequestBody GameContentDto.SaveRequest req) {
        return gameService.createContent(req);
    }

    @PutMapping("/contents/{id}")
    public GameContentDto update(@PathVariable Long id,
                                 @Valid @RequestBody GameContentDto.SaveRequest req) {
        return gameService.updateContent(id, req);
    }

    @DeleteMapping("/contents/{id}")
    public void delete(@PathVariable Long id) {
        gameService.deleteContent(id);
    }

    // ─── 방 ─────────────────────────────────────────────

    @GetMapping("/rooms")
    public List<GameRoomDto> rooms() {
        return gameService.listRoomsForAdmin();
    }

    /** 방 강제 종료. */
    @PostMapping("/rooms/{code}/close")
    public void close(@PathVariable String code) {
        gameService.closeByAdmin(code);
    }
}
