package com.damso.match.domain.game.service;

import com.damso.match.api.admin.game.dto.GameContentDto;
import com.damso.match.api.app.game.dto.GameStateDto;
import com.damso.match.domain.game.dto.GameRoomDto;
import com.damso.match.domain.game.entity.GameAnswer;
import com.damso.match.domain.game.entity.GameContent;
import com.damso.match.domain.game.entity.GamePlayer;
import com.damso.match.domain.game.entity.GamePlayerRole;
import com.damso.match.domain.game.entity.GameRoom;
import com.damso.match.domain.game.entity.GameRoomStatus;
import com.damso.match.domain.game.entity.GameRound;
import com.damso.match.domain.game.entity.GameRoundStatus;
import com.damso.match.domain.game.entity.GameType;
import com.damso.match.domain.game.entity.PlayerRole;
import com.damso.match.domain.game.repository.GameAnswerRepository;
import com.damso.match.domain.game.repository.GameContentRepository;
import com.damso.match.domain.game.repository.GamePlayerRepository;
import com.damso.match.domain.game.repository.GamePlayerRoleRepository;
import com.damso.match.domain.game.repository.GameRoomRepository;
import com.damso.match.domain.game.repository.GameRoundRepository;
import com.damso.match.domain.member.entity.AppUser;
import com.damso.match.domain.setting.service.FeatureService;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 게임방.
 *  - 로그인한 회원이면 누구나 방을 만들 수 있고 인원 제한은 없다.
 *  - 방장이 게임을 시작하고 결과를 공개한다. 나머지는 답만 낸다.
 *  - 뽑기·문항 선택은 전부 서버에서 한다. 화면에서 뽑으면 사람마다 결과가 달라진다.
 *  - 화면은 상태 하나(GameStateDto)를 주기적으로 다시 불러 갱신한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GameService {

    private static final SecureRandom RANDOM = new SecureRandom();
    /** 방 코드 자릿수. 전화로 불러주기 쉽게 숫자만 쓴다. */
    private static final int CODE_LENGTH = 4;
    /** 목록에 보여줄 방 최대 수. */
    private static final int ROOM_LIST_LIMIT = 30;
    /** 아무도 보고 있지 않은 채로 이만큼 지나면 방을 닫는다. */
    private static final int EMPTY_ROOM_MINUTES = 10;

    private final GameRoomRepository roomRepo;
    private final GamePlayerRepository playerRepo;
    private final GameRoundRepository roundRepo;
    private final GameAnswerRepository answerRepo;
    private final GameContentRepository contentRepo;
    private final GamePlayerRoleRepository roleRepo;
    private final FeatureService featureService;

    // ─── 방 만들기 / 들어가기 ────────────────────────────

    @Transactional
    public GameStateDto create(AppUser user, String title) {
        featureService.requireGameRoom();
        GameRoom room = roomRepo.save(GameRoom.builder()
                .code(nextCode())
                .title(emptyToNull(title))
                .hostUserId(user.getId())
                .status(GameRoomStatus.WAITING)
                .updatedAt(LocalDateTime.now())
                .build());
        joinRoom(room, user);
        return state(user, room.getCode());
    }

    /**
     * 열려 있는 방 목록.
     * 아무도 보고 있지 않은 방은 목록에서 뺀다. 앱을 그냥 닫고 가버린 방이 계속 남아
     * "1명 참여 중"으로 보이면 들어가 봐야 아무도 없다.
     */
    @Transactional
    public List<GameRoomDto> listOpenRooms(AppUser user) {
        featureService.requireGameRoom();
        List<GameRoomDto> out = new ArrayList<>();
        for (GameRoom room : roomRepo.findByStatusNotOrderByCreatedAtDesc(GameRoomStatus.CLOSED)) {
            if (!room.isOpen()) continue;
            List<GamePlayer> players = playerRepo.findByRoomIdOrderByJoinedAtAsc(room.getId());
            long active = players.stream().filter(GamePlayer::isActive).count();
            if (active == 0) {
                // 잠깐 화면을 벗어난 것일 수 있어 바로 닫지는 않고, 한참 조용하면 정리한다.
                if (room.getUpdatedAt() != null
                        && room.getUpdatedAt().plusMinutes(EMPTY_ROOM_MINUTES).isBefore(LocalDateTime.now())) {
                    closeRoom(room);
                }
                continue;
            }
            out.add(roomDto(room, players, user));
            if (out.size() >= ROOM_LIST_LIMIT) break;
        }
        return out;
    }

    @Transactional
    public GameStateDto join(AppUser user, String code) {
        featureService.requireGameRoom();
        GameRoom room = openRoom(code);
        joinRoom(room, user);
        return state(user, code);
    }

    @Transactional
    public void leave(AppUser user, String code) {
        GameRoom room = requireRoom(code);
        GamePlayer me = playerRepo.findByRoomIdAndUserId(room.getId(), user.getId()).orElse(null);
        if (me == null) return;
        me.setLeftAt(LocalDateTime.now());
        playerRepo.save(me);

        // 사회자가 나가면 자리를 비워 다른 사람이 맡을 수 있게 한다.
        if (user.getId().equals(room.getModeratorUserId())) {
            room.setModeratorUserId(null);
            touchRoom(room);
        }

        List<GamePlayer> remain = playerRepo.findByRoomIdOrderByJoinedAtAsc(room.getId()).stream()
                .filter(p -> p.getLeftAt() == null)
                .toList();

        // 아무도 안 남았으면 방을 닫는다. 빈 방을 목록에 두지 않는다.
        if (remain.isEmpty()) {
            closeRoom(room);
            return;
        }
        // 방장이 나갔으면 남은 사람 중 먼저 들어온 사람이 이어받는다.
        if (room.getHostUserId().equals(user.getId())) {
            room.setHostUserId(remain.get(0).getUserId());
            touchRoom(room);
        }
    }

    /**
     * 마피아 사회자 맡기 / 내려놓기.
     * 먼저 자처한 한 명이 갖는다. 방장이 아니어도 되고, 남이 맡고 있으면 뺏지 못한다.
     */
    @Transactional
    public GameStateDto setModerator(AppUser user, String code, boolean take) {
        featureService.requireGameRoom();
        GameRoom room = openRoom(code);
        requirePlayer(room, user);

        Long current = room.getModeratorUserId();
        if (take) {
            if (current != null && !current.equals(user.getId())) {
                throw new IllegalStateException("이미 다른 분이 사회자를 맡고 있어요.");
            }
            room.setModeratorUserId(user.getId());
        } else {
            if (current == null || !current.equals(user.getId())) {
                throw new IllegalStateException("사회자가 아니에요.");
            }
            room.setModeratorUserId(null);
        }
        touchRoom(room);
        return state(user, code);
    }

    @Transactional
    public void close(AppUser user, String code) {
        GameRoom room = requireRoom(code);
        requireHost(room, user);
        closeRoom(room);
    }

    // ─── 상태 ───────────────────────────────────────────

    /** 화면이 주기적으로 부르는 곳. 부르는 김에 '접속 중' 표시를 갱신한다. */
    @Transactional
    public GameStateDto state(AppUser user, String code) {
        featureService.requireGameRoom();
        GameRoom room = requireRoom(code);
        touch(room, user);

        List<GamePlayer> players = playerRepo.findByRoomIdOrderByJoinedAtAsc(room.getId());
        GameRound round = roundRepo.findFirstByRoomIdOrderByIdDesc(room.getId()).orElse(null);

        Map<Long, String> nameByUser = new HashMap<>();
        players.forEach(p -> nameByUser.put(p.getUserId(), p.getDisplayName()));

        return new GameStateDto(
                room.getCode(),
                room.getTitle(),
                room.getStatus().name(),
                room.getStatus().label(),
                nameByUser.getOrDefault(room.getHostUserId(), "방장"),
                room.getHostUserId().equals(user.getId()),
                room.getModeratorUserId() == null
                        ? null : nameByUser.getOrDefault(room.getModeratorUserId(), "사회자"),
                user.getId().equals(room.getModeratorUserId()),
                players.stream()
                        .filter(p -> p.getLeftAt() == null)
                        .map(p -> new GameStateDto.Player(
                                p.getUserId(), p.getDisplayName(), p.isActive(),
                                p.getUserId().equals(room.getHostUserId()),
                                p.getUserId().equals(room.getModeratorUserId()),
                                p.getUserId().equals(user.getId())))
                        .toList(),
                (int) players.stream().filter(GamePlayer::isActive).count(),
                round == null ? null : roundView(round, room, user, nameByUser),
                availableGames());
    }

    // ─── 라운드 ─────────────────────────────────────────

    /** 방장이 게임을 시작한다. 문항 선택과 뽑기는 여기(서버)에서 한다. */
    @Transactional
    public GameStateDto startRound(AppUser user, String code, GameType type) {
        featureService.requireGameRoom();
        if (!featureService.gameEnabled(type)) {
            throw new IllegalStateException(type.label() + "는 지금 사용할 수 없어요.");
        }
        GameRoom room = openRoom(code);
        requireHost(room, user);

        List<GamePlayer> active = playerRepo.findByRoomIdOrderByJoinedAtAsc(room.getId()).stream()
                .filter(GamePlayer::isActive)
                .toList();
        // 마피아는 사회자가 진행만 맡으므로 인원에서 뺀다.
        if (type == GameType.MAFIA && room.getModeratorUserId() != null) {
            active = active.stream()
                    .filter(p -> !p.getUserId().equals(room.getModeratorUserId()))
                    .toList();
        }
        if (active.size() < type.minPlayers()) {
            throw new IllegalStateException(
                    type.label() + " · " + type.minPlayers() + "명부터 할 수 있어요. "
                            + "지금 " + active.size() + "명이에요.");
        }

        GameRound round = GameRound.builder()
                .roomId(room.getId())
                .gameType(type)
                .status(GameRoundStatus.OPEN)
                .build();

        switch (type) {
            case BALANCE -> {
                GameContent picked = pickContent(GameType.BALANCE, room.getId());
                if (picked == null) {
                    throw new IllegalStateException("등록된 밸런스 게임 문항이 없어요. 관리자에게 문의해주세요.");
                }
                round.setOptionA(picked.getOptionA());
                round.setOptionB(picked.getOptionB());
            }
            case PICK -> {
                // 랜덤 뽑기는 답을 받을 것이 없어 바로 공개 상태로 만든다.
                round.setWinnerName(active.get(RANDOM.nextInt(active.size())).getDisplayName());
                GameContent mission = pickContent(GameType.PICK, room.getId());
                if (mission != null) round.setOptionA(mission.getOptionA());
                round.setStatus(GameRoundStatus.REVEALED);
                round.setRevealedAt(LocalDateTime.now());
            }
            case LIAR -> {
                GameContent word = pickContent(GameType.LIAR, room.getId());
                if (word == null) {
                    throw new IllegalStateException("등록된 라이어 게임 제시어가 없어요. 관리자에게 문의해주세요.");
                }
                round.setOptionA(word.getOptionA());   // 제시어
                round.setOptionB(word.getOptionB());   // 카테고리
                round.setStatus(GameRoundStatus.ROLE);
            }
            case MAFIA -> round.setStatus(GameRoundStatus.ROLE);
        }
        roundRepo.save(round);

        if (type.isRoleGame()) assignRoles(round, active);

        room.setStatus(round.isRevealed() ? GameRoomStatus.WAITING : GameRoomStatus.PLAYING);
        room.setUpdatedAt(LocalDateTime.now());
        roomRepo.save(room);
        return state(user, code);
    }

    /**
     * 역할을 다 확인했으면 방장이 투표를 연다. (라이어·마피아)
     * 이야기는 자리에서 하는 것이라 서버가 관여하지 않는다. 방장이 때를 정한다.
     */
    @Transactional
    public GameStateDto openVote(AppUser user, String code, Long roundId) {
        featureService.requireGameRoom();
        GameRoom room = openRoom(code);
        requireHost(room, user);

        GameRound round = requireRound(room, roundId);
        if (!round.getGameType().isRoleGame()) {
            throw new IllegalStateException("투표하는 게임이 아니에요.");
        }
        if (round.getStatus() == GameRoundStatus.ROLE) {
            round.setStatus(GameRoundStatus.VOTE);
            roundRepo.save(round);
        }
        touchRoom(room);
        return state(user, code);
    }

    /** 답 내기(밸런스) 또는 지목하기(라이어·마피아). 공개 전이면 마음을 바꿔도 된다. */
    @Transactional
    public GameStateDto answer(AppUser user, String code, Long roundId, String value) {
        featureService.requireGameRoom();
        GameRoom room = openRoom(code);
        requirePlayer(room, user);

        GameRound round = requireRound(room, roundId);
        if (round.isRevealed()) throw new IllegalStateException("이미 결과가 공개된 게임이에요.");

        String v;
        if (round.getGameType() == GameType.BALANCE) {
            v = "B".equalsIgnoreCase(value) ? "B" : "A";
        } else if (round.getGameType().isRoleGame()) {
            if (round.getStatus() != GameRoundStatus.VOTE) {
                throw new IllegalStateException("아직 투표할 때가 아니에요.");
            }
            // 이번 판에 역할을 받은 사람만 투표한다.
            // 늦게 들어온 사람과 (역할을 받지 않는) 마피아 사회자가 여기서 걸린다.
            Set<Long> inRound = roleRepo.findByRoundId(round.getId()).stream()
                    .map(GamePlayerRole::getUserId).collect(Collectors.toSet());
            if (!inRound.contains(user.getId())) {
                throw new IllegalStateException("이번 판에 참여하지 않아 투표할 수 없어요.");
            }
            // 지목도 이번 판을 함께 하는 사람 중에서만 고른다.
            Long target = parseLong(value);
            if (target == null || !inRound.contains(target)) {
                throw new IllegalArgumentException("이번 판에 참여한 사람 중에서 골라주세요.");
            }
            v = String.valueOf(target);
        } else {
            throw new IllegalStateException("답을 낼 수 있는 게임이 아니에요.");
        }

        GameAnswer answer = answerRepo.findByRoundIdAndUserId(roundId, user.getId())
                .orElseGet(() -> GameAnswer.builder().roundId(roundId).userId(user.getId()).build());
        answer.setValue(v);
        answerRepo.save(answer);

        touchRoom(room);
        return state(user, code);
    }

    /** 방장이 결과를 공개한다. 공개해야 집계가 내려간다. */
    @Transactional
    public GameStateDto reveal(AppUser user, String code, Long roundId) {
        featureService.requireGameRoom();
        GameRoom room = openRoom(code);
        requireHost(room, user);

        GameRound round = requireRound(room, roundId);
        if (!round.isRevealed()) {
            round.setStatus(GameRoundStatus.REVEALED);
            round.setRevealedAt(LocalDateTime.now());
            roundRepo.save(round);
        }
        room.setStatus(GameRoomStatus.WAITING);
        room.setUpdatedAt(LocalDateTime.now());
        roomRepo.save(room);
        return state(user, code);
    }

    // ─── 관리자 ─────────────────────────────────────────

    public List<GameRoomDto> listRoomsForAdmin() {
        List<GameRoomDto> out = new ArrayList<>();
        for (GameRoom room : roomRepo.findAllByOrderByCreatedAtDesc()) {
            out.add(roomDto(room, playerRepo.findByRoomIdOrderByJoinedAtAsc(room.getId()), null));
            if (out.size() >= ROOM_LIST_LIMIT) break;
        }
        return out;
    }

    @Transactional
    public void closeByAdmin(String code) {
        closeRoom(requireRoom(code));
    }

    public List<GameContentDto> listContents() {
        return contentRepo.findAllByOrderByTypeAscSortOrderAscIdAsc().stream()
                .map(GameContentDto::from).toList();
    }

    @Transactional
    public GameContentDto createContent(GameContentDto.SaveRequest req) {
        GameContent c = new GameContent();
        applyContent(c, req);
        return GameContentDto.from(contentRepo.save(c));
    }

    @Transactional
    public GameContentDto updateContent(Long id, GameContentDto.SaveRequest req) {
        GameContent c = contentRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("문항을 찾을 수 없습니다."));
        applyContent(c, req);
        return GameContentDto.from(contentRepo.save(c));
    }

    @Transactional
    public void deleteContent(Long id) {
        contentRepo.deleteById(id);
    }

    /** 첫 실행 때 문항이 하나도 없으면 기본 문항을 넣어 둔다. */
    @Transactional
    public void seedDefaultsIfEmpty() {
        if (contentRepo.countByType(GameType.BALANCE) == 0) {
            String[][] pairs = {
                    {"소주", "맥주"},
                    {"치킨은 후라이드", "치킨은 양념"},
                    {"여행은 계획대로", "여행은 발길 닿는 대로"},
                    {"평생 라면만", "평생 김밥만"},
                    {"돈 많은 백수", "일 많은 부자"},
                    {"먼저 연락하는 사람", "기다리는 사람"},
                    {"산", "바다"},
                    {"과거로 가기", "미래로 가기"},
            };
            int order = 0;
            for (String[] pair : pairs) {
                contentRepo.save(GameContent.builder()
                        .type(GameType.BALANCE).optionA(pair[0]).optionB(pair[1])
                        .visible(Boolean.TRUE).sortOrder(order++).build());
            }
        }
        if (contentRepo.countByType(GameType.LIAR) == 0) {
            String[][] words = {
                    {"사과", "과일"}, {"치킨", "음식"}, {"제주도", "여행지"}, {"고양이", "동물"},
                    {"소주", "술"}, {"지하철", "교통"}, {"편의점", "장소"}, {"크리스마스", "기념일"},
                    {"여름", "계절"}, {"바다", "자연"}, {"영화관", "장소"}, {"떡볶이", "음식"},
                    {"강아지", "동물"}, {"커피", "음료"}, {"놀이공원", "장소"},
            };
            int order = 0;
            for (String[] w : words) {
                contentRepo.save(GameContent.builder()
                        .type(GameType.LIAR).optionA(w[0]).optionB(w[1])
                        .visible(Boolean.TRUE).sortOrder(order++).build());
            }
        }
        if (contentRepo.countByType(GameType.PICK) == 0) {
            String[] missions = {
                    "옆 사람에게 칭찬 한마디",
                    "최근에 가장 웃겼던 일 이야기하기",
                    "휴대폰 최근 사진 한 장 보여주기",
                    "다음 잔 건배사 하기",
                    "본인 최애 노래 한 소절",
            };
            int order = 0;
            for (String mission : missions) {
                contentRepo.save(GameContent.builder()
                        .type(GameType.PICK).optionA(mission)
                        .visible(Boolean.TRUE).sortOrder(order++).build());
            }
        }
    }

    // ─── 헬퍼 ───────────────────────────────────────────

    private void joinRoom(GameRoom room, AppUser user) {
        GamePlayer player = playerRepo.findByRoomIdAndUserId(room.getId(), user.getId())
                .orElseGet(() -> GamePlayer.builder()
                        .roomId(room.getId()).userId(user.getId()).build());
        player.setDisplayName(user.displayName());
        player.setLeftAt(null);
        player.setLastSeenAt(LocalDateTime.now());
        playerRepo.save(player);
        touchRoom(room);
    }

    /** 상태를 물어볼 때마다 '방금 봤다'고 적어 둔다. */
    private void touch(GameRoom room, AppUser user) {
        playerRepo.findByRoomIdAndUserId(room.getId(), user.getId()).ifPresent(p -> {
            p.setLastSeenAt(LocalDateTime.now());
            if (p.getLeftAt() != null) p.setLeftAt(null);
            playerRepo.save(p);
        });
    }

    private void touchRoom(GameRoom room) {
        room.setUpdatedAt(LocalDateTime.now());
        roomRepo.save(room);
    }

    private void closeRoom(GameRoom room) {
        room.setStatus(GameRoomStatus.CLOSED);
        room.setClosedAt(LocalDateTime.now());
        room.setUpdatedAt(LocalDateTime.now());
        roomRepo.save(room);
    }

    private GameRoomDto roomDto(GameRoom room, List<GamePlayer> players, AppUser user) {
        String hostName = players.stream()
                .filter(p -> p.getUserId().equals(room.getHostUserId()))
                .map(GamePlayer::getDisplayName).findFirst().orElse("방장");
        boolean joined = user != null && players.stream()
                .anyMatch(p -> p.getUserId().equals(user.getId()) && p.getLeftAt() == null);
        return new GameRoomDto(
                room.getCode(), room.getTitle(), hostName,
                room.getStatus().name(), room.getStatus().label(),
                // 실제로 화면을 보고 있는 사람 수. 목록에서는 이게 더 정확하다.
                (int) players.stream().filter(GamePlayer::isActive).count(),
                joined, room.getCreatedAt());
    }

    private GameStateDto.Round roundView(GameRound round, GameRoom room, AppUser user,
                                         Map<Long, String> nameByUser) {
        List<GameAnswer> answers = answerRepo.findByRoundId(round.getId());
        String mine = answers.stream()
                .filter(a -> a.getUserId().equals(user.getId()))
                .map(GameAnswer::getValue).findFirst().orElse(null);

        boolean revealed = round.isRevealed();
        boolean roleGame = round.getGameType().isRoleGame();

        // ─ 밸런스 게임 집계 (공개 뒤에만) ─
        List<String> namesA = new ArrayList<>();
        List<String> namesB = new ArrayList<>();
        if (revealed && round.getGameType() == GameType.BALANCE) {
            for (GameAnswer a : answers) {
                String name = nameByUser.getOrDefault(a.getUserId(), "회원");
                if ("B".equals(a.getValue())) namesB.add(name);
                else namesA.add(name);
            }
        }

        // ─ 역할 게임 ─
        String myRole = null;
        String myRoleLabel = null;
        String myRoleHint = null;
        List<GameStateDto.RoleCard> allRoles = new ArrayList<>();
        List<Long> playerIds = new ArrayList<>();
        String keyword = null;
        String category = null;
        List<String> mates = new ArrayList<>();
        List<GameStateDto.Vote> votes = new ArrayList<>();
        List<String> revealedRoles = new ArrayList<>();
        String outcome = null;

        if (roleGame) {
            List<GamePlayerRole> roles = roleRepo.findByRoundId(round.getId());
            // 이번 판을 실제로 하는 사람들. 늦게 들어온 사람과 마피아 사회자는 여기 없다.
            roles.forEach(r -> playerIds.add(r.getUserId()));
            PlayerRole mineRole = roles.stream()
                    .filter(r -> r.getUserId().equals(user.getId()))
                    .map(GamePlayerRole::getRole).findFirst().orElse(null);
            if (mineRole != null) {
                myRole = mineRole.name();
                myRoleLabel = mineRole.label();
                myRoleHint = mineRole.hint(round.getGameType());
            }
            // 사회자는 마피아에서만 쓰는 자리다. 진행을 해야 하므로 전체 역할을 본다.
            // 라이어에서는 사회자도 그냥 한 명의 참가자라 남의 역할을 볼 수 없다.
            if (round.getGameType() == GameType.MAFIA
                    && user.getId().equals(room.getModeratorUserId())) {
                for (GamePlayerRole r : roles) {
                    allRoles.add(new GameStateDto.RoleCard(
                            nameByUser.getOrDefault(r.getUserId(), "회원"),
                            r.getRole().name(), r.getRole().label()));
                }
            }
            // 제시어는 라이어에게 보이면 안 된다.
            if (round.getGameType() == GameType.LIAR && mineRole == PlayerRole.CITIZEN) {
                keyword = round.getOptionA();
                category = round.getOptionB();
            }
            // 마피아끼리는 서로를 안다.
            if (mineRole == PlayerRole.MAFIA) {
                for (GamePlayerRole r : roles) {
                    if (r.getRole() != PlayerRole.MAFIA) continue;
                    if (r.getUserId().equals(user.getId())) continue;
                    mates.add(nameByUser.getOrDefault(r.getUserId(), "회원"));
                }
            }
            if (revealed) {
                votes = tally(answers, nameByUser);
                // 라이어는 한 판으로 끝나니 정체를 밝힌다.
                // 마피아는 밤을 사회자가 이어서 진행하므로 남은 역할을 밝히지 않는다.
                if (round.getGameType() == GameType.LIAR) {
                    for (GamePlayerRole r : roles) {
                        if (r.getRole() == PlayerRole.LIAR) {
                            revealedRoles.add(nameByUser.getOrDefault(r.getUserId(), "회원"));
                        }
                    }
                }
                outcome = outcome(round, roles, answers, nameByUser);
                // 공개 뒤에는 제시어를 모두에게 보여준다.
                if (round.getGameType() == GameType.LIAR) {
                    keyword = round.getOptionA();
                    category = round.getOptionB();
                }
            }
        }

        // 라이어의 제시어는 optionA/optionB 에 들어 있다. 역할 게임에서는 그대로 내려보내면
        // 화면에 안 그리더라도 응답만 열어보면 답을 알 수 있어, 아예 비워서 내려보낸다.
        String optionA = roleGame ? null : round.getOptionA();
        String optionB = roleGame ? null : round.getOptionB();

        return new GameStateDto.Round(
                round.getId(), round.getGameType(), round.getGameType().label(),
                round.getStatus().name(), round.getStatus().label(), revealed,
                optionA, optionB, round.getWinnerName(),
                mine, answers.size(),
                namesA.size(), namesB.size(), namesA, namesB,
                myRole, myRoleLabel, keyword, category, myRoleHint, mates, allRoles,
                playerIds, votes, revealedRoles, outcome);
    }

    /**
     * 역할 나눠주기.
     *  - 라이어: 한 명만 라이어, 나머지 시민.
     *  - 마피아: 보편적으로 쓰는 구성을 따른다.
     *      마피아 = (참가자-1)/3 내림, 최소 1명   → 4~6명 1, 7~9명 2, 10~12명 3
     *      경찰은 5명부터, 의사는 6명부터 한 명씩. 나머지는 전원 시민.
     *    4명에서 경찰까지 넣으면 마피아가 너무 불리해서 4명은 마피아 1·시민 3으로 둔다.
     */
    private void assignRoles(GameRound round, List<GamePlayer> active) {
        List<GamePlayer> shuffled = new ArrayList<>(active);
        Collections.shuffle(shuffled, RANDOM);

        List<PlayerRole> deck = new ArrayList<>();
        if (round.getGameType() == GameType.LIAR) {
            deck.add(PlayerRole.LIAR);
        } else {
            int n = shuffled.size();
            int mafia = Math.max(1, (n - 1) / 3);
            for (int i = 0; i < mafia; i++) deck.add(PlayerRole.MAFIA);
            if (n >= 5) deck.add(PlayerRole.POLICE);
            if (n >= 6) deck.add(PlayerRole.DOCTOR);
        }
        while (deck.size() < shuffled.size()) deck.add(PlayerRole.CITIZEN);

        for (int i = 0; i < shuffled.size(); i++) {
            roleRepo.save(GamePlayerRole.builder()
                    .roundId(round.getId())
                    .userId(shuffled.get(i).getUserId())
                    .role(deck.get(i))
                    .build());
        }
    }

    /** 득표 집계 — 많이 받은 순. */
    private List<GameStateDto.Vote> tally(List<GameAnswer> answers, Map<Long, String> nameByUser) {
        Map<Long, List<String>> byTarget = new LinkedHashMap<>();
        for (GameAnswer a : answers) {
            Long target = parseLong(a.getValue());
            if (target == null) continue;
            byTarget.computeIfAbsent(target, k -> new ArrayList<>())
                    .add(nameByUser.getOrDefault(a.getUserId(), "회원"));
        }
        List<GameStateDto.Vote> out = new ArrayList<>();
        byTarget.forEach((target, voters) -> out.add(new GameStateDto.Vote(
                nameByUser.getOrDefault(target, "회원"), voters.size(), voters)));
        out.sort(Comparator.comparingInt(GameStateDto.Vote::count).reversed());
        return out;
    }

    /**
     * 결과 한 줄.
     * 최다 득표자가 숨은 역할이면 시민이 이긴다. 동점이면 아무도 지목하지 못한 것으로 보고
     * 라이어·마피아가 살아남는다.
     */
    private String outcome(GameRound round, List<GamePlayerRole> roles,
                           List<GameAnswer> answers, Map<Long, String> nameByUser) {
        boolean liar = round.getGameType() == GameType.LIAR;
        String hiddenLabel = liar ? "라이어" : "마피아";

        Map<Long, Integer> counts = new HashMap<>();
        for (GameAnswer a : answers) {
            Long target = parseLong(a.getValue());
            if (target != null) counts.merge(target, 1, Integer::sum);
        }
        if (counts.isEmpty()) return "아무도 지목하지 않아 " + hiddenLabel + "이(가) 살아남았어요!";

        int top = counts.values().stream().max(Integer::compareTo).orElse(0);
        List<Long> topIds = counts.entrySet().stream()
                .filter(e -> e.getValue() == top).map(Map.Entry::getKey).toList();
        if (topIds.size() > 1) {
            return "표가 갈려 아무도 지목되지 않았어요. " + hiddenLabel + " 승리!";
        }

        Long picked = topIds.get(0);
        String name = nameByUser.getOrDefault(picked, "회원");
        PlayerRole pickedRole = roles.stream()
                .filter(r -> r.getUserId().equals(picked))
                .map(GamePlayerRole::getRole).findFirst().orElse(PlayerRole.CITIZEN);

        if (liar) {
            // 한 판으로 끝나는 게임이라 승패까지 말해준다.
            return pickedRole == PlayerRole.LIAR
                    ? name + "님이 라이어였어요. 시민 승리!"
                    : name + "님은 시민이었어요. 라이어 승리!";
        }
        // 마피아는 밤이 이어지므로 지목 결과만 알리고 판정은 사회자에게 맡긴다.
        return pickedRole == PlayerRole.MAFIA
                ? name + "님이 지목됐어요 — 마피아였습니다!"
                : name + "님이 지목됐어요 — " + pickedRole.label() + "이었습니다.";
    }

    private static Long parseLong(String v) {
        try {
            return Long.parseLong(v.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private GameRound requireRound(GameRoom room, Long roundId) {
        return roundRepo.findById(roundId)
                .filter(r -> r.getRoomId().equals(room.getId()))
                .orElseThrow(() -> new IllegalArgumentException("진행 중인 게임을 찾을 수 없어요."));
    }

    private List<GameStateDto.GameOption> availableGames() {
        List<GameStateDto.GameOption> out = new ArrayList<>();
        for (GameType type : GameType.values()) {
            if (!featureService.gameEnabled(type)) continue;
            out.add(new GameStateDto.GameOption(type, type.label(), type.description()));
        }
        return out;
    }

    /**
     * 문항 하나 고르기.
     * 그 방에서 최근에 쓴 문항은 빼서 같은 문항이 연달아 나오지 않게 한다.
     */
    private GameContent pickContent(GameType type, Long roomId) {
        List<GameContent> pool = contentRepo.findByType(type).stream()
                .filter(GameContent::isVisibleItem)
                .toList();
        if (pool.isEmpty()) return null;

        Set<String> used = new HashSet<>();
        for (GameRound past : roundRepo.findByRoomIdOrderByIdDesc(roomId)) {
            if (past.getGameType() != type) continue;
            if (past.getOptionA() != null) used.add(past.getOptionA());
            if (used.size() >= pool.size() - 1) break;   // 다 쓰면 처음부터 다시
        }
        List<GameContent> fresh = pool.stream()
                .filter(c -> !used.contains(c.getOptionA()))
                .toList();
        List<GameContent> from = fresh.isEmpty() ? pool : fresh;
        return from.get(RANDOM.nextInt(from.size()));
    }

    private void applyContent(GameContent c, GameContentDto.SaveRequest req) {
        if (req.type() == GameType.BALANCE
                && (req.optionB() == null || req.optionB().isBlank())) {
            throw new IllegalArgumentException("밸런스 게임은 선택지를 두 개 입력해주세요.");
        }
        c.setType(req.type());
        c.setOptionA(req.optionA().trim());
        c.setOptionB(emptyToNull(req.optionB()));
        c.setVisible(!Boolean.FALSE.equals(req.visible()));
        if (req.sortOrder() != null) c.setSortOrder(req.sortOrder());
        c.setUpdatedAt(LocalDateTime.now());
    }

    private GameRoom requireRoom(String code) {
        return roomRepo.findByCode(normalize(code))
                .orElseThrow(() -> new IllegalArgumentException("그런 방이 없어요. 코드를 확인해주세요."));
    }

    private GameRoom openRoom(String code) {
        GameRoom room = requireRoom(code);
        if (!room.isOpen()) throw new IllegalStateException("이미 끝난 방이에요.");
        return room;
    }

    private void requireHost(GameRoom room, AppUser user) {
        if (!room.getHostUserId().equals(user.getId())) {
            throw new IllegalStateException("방장만 할 수 있어요.");
        }
    }

    private void requirePlayer(GameRoom room, AppUser user) {
        playerRepo.findByRoomIdAndUserId(room.getId(), user.getId())
                .filter(p -> p.getLeftAt() == null)
                .orElseThrow(() -> new IllegalStateException("방에 먼저 들어와주세요."));
    }

    /** 겹치지 않는 네 자리 코드. */
    private String nextCode() {
        for (int i = 0; i < 200; i++) {
            String code = String.format("%0" + CODE_LENGTH + "d", RANDOM.nextInt(10000));
            if (!roomRepo.existsByCode(code)) return code;
        }
        throw new IllegalStateException("방 코드를 만들지 못했어요. 잠시 후 다시 시도해주세요.");
    }

    private static String normalize(String code) {
        return code == null ? "" : code.trim();
    }

    private static String emptyToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
