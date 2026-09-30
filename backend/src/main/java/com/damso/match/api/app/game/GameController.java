package com.damso.match.api.app.game;

import com.damso.match.api.app.game.dto.GameStateDto;
import com.damso.match.domain.game.dto.GameRoomDto;
import com.damso.match.domain.game.entity.GameType;
import com.damso.match.domain.game.service.GameService;
import com.damso.match.domain.member.entity.AppUser;
import com.damso.match.domain.member.service.AuthService;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 게임방 API. 모두 로그인 필수다.
 *  - 화면은 /rooms/{code} 를 주기적으로 다시 불러 상태를 갱신한다.
 *  - 게임방을 꺼두면(관리자 설정) 모든 요청이 막힌다.
 */
@RestController
@RequestMapping("/api/app/games")
@RequiredArgsConstructor
public class GameController {

    private final GameService gameService;
    private final AuthService authService;

    /** 열려 있는 방 목록. */
    @GetMapping("/rooms")
    public List<GameRoomDto> rooms(@RequestHeader(value = "Authorization", required = false) String auth) {
        return gameService.listOpenRooms(authService.requireUser(auth));
    }

    @PostMapping("/rooms")
    public GameStateDto create(@RequestHeader(value = "Authorization", required = false) String auth,
                               @RequestBody(required = false) CreateRequest req) {
        AppUser user = authService.requireUser(auth);
        return gameService.create(user, req == null ? null : req.title);
    }

    /** 방 상태 — 화면이 주기적으로 부르는 곳. */
    @GetMapping("/rooms/{code}")
    public GameStateDto state(@RequestHeader(value = "Authorization", required = false) String auth,
                              @PathVariable String code) {
        return gameService.state(authService.requireUser(auth), code);
    }

    @PostMapping("/rooms/{code}/join")
    public GameStateDto join(@RequestHeader(value = "Authorization", required = false) String auth,
                             @PathVariable String code) {
        return gameService.join(authService.requireUser(auth), code);
    }

    @PostMapping("/rooms/{code}/leave")
    public void leave(@RequestHeader(value = "Authorization", required = false) String auth,
                      @PathVariable String code) {
        gameService.leave(authService.requireUser(auth), code);
    }

    /** 마피아 사회자 맡기 / 내려놓기. 먼저 자처한 한 명이 갖는다. */
    @PostMapping("/rooms/{code}/moderator")
    public GameStateDto moderator(@RequestHeader(value = "Authorization", required = false) String auth,
                                  @PathVariable String code,
                                  @RequestBody(required = false) ModeratorRequest req) {
        AppUser user = authService.requireUser(auth);
        return gameService.setModerator(user, code, req == null || !Boolean.FALSE.equals(req.take));
    }

    /** 방 종료 — 방장만. */
    @PostMapping("/rooms/{code}/close")
    public void close(@RequestHeader(value = "Authorization", required = false) String auth,
                      @PathVariable String code) {
        gameService.close(authService.requireUser(auth), code);
    }

    /** 게임 시작 — 방장만. 문항 선택·뽑기는 서버가 한다. */
    @PostMapping("/rooms/{code}/rounds")
    public GameStateDto start(@RequestHeader(value = "Authorization", required = false) String auth,
                              @PathVariable String code,
                              @RequestBody StartRequest req) {
        AppUser user = authService.requireUser(auth);
        return gameService.startRound(user, code, req.gameType);
    }

    /** 투표 열기 — 방장만. 역할을 다 확인한 뒤 누른다. */
    @PostMapping("/rooms/{code}/rounds/{roundId}/vote-open")
    public GameStateDto openVote(@RequestHeader(value = "Authorization", required = false) String auth,
                                 @PathVariable String code, @PathVariable Long roundId) {
        return gameService.openVote(authService.requireUser(auth), code, roundId);
    }

    @PostMapping("/rooms/{code}/rounds/{roundId}/answer")
    public GameStateDto answer(@RequestHeader(value = "Authorization", required = false) String auth,
                               @PathVariable String code, @PathVariable Long roundId,
                               @RequestBody AnswerRequest req) {
        AppUser user = authService.requireUser(auth);
        return gameService.answer(user, code, roundId, req.value);
    }

    /** 결과 공개 — 방장만. */
    @PostMapping("/rooms/{code}/rounds/{roundId}/reveal")
    public GameStateDto reveal(@RequestHeader(value = "Authorization", required = false) String auth,
                               @PathVariable String code, @PathVariable Long roundId) {
        return gameService.reveal(authService.requireUser(auth), code, roundId);
    }

    public static class CreateRequest {
        public String title;
    }

    public static class ModeratorRequest {
        public Boolean take;
    }

    public static class StartRequest {
        public GameType gameType;
    }

    public static class AnswerRequest {
        public String value;
    }
}
