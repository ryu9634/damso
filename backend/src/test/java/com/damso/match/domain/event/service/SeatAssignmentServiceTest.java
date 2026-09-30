package com.damso.match.domain.event.service;

import com.damso.match.domain.dating.service.MatchScoreService;
import com.damso.match.domain.event.entity.Customer;
import com.damso.match.domain.event.entity.CustomerStatus;
import com.damso.match.domain.event.entity.Preference;
import com.damso.match.domain.event.entity.SeatAssignment;
import com.damso.match.domain.event.repository.CustomerRepository;
import com.damso.match.domain.event.repository.PreferenceRepository;
import com.damso.match.domain.event.repository.SeatAssignmentRepository;
import com.damso.match.global.common.Gender;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

/**
 * 좌석 배치 알고리즘 검증.
 *
 * 핵심 전략: 서비스가 내놓은 배치의 <b>원점수 합</b>을, 테스트가 독립적으로 구현한
 * 브루트포스 오라클의 최적값과 대조한다. 구현을 흉내 내지 않고 "최적해인가"만 묻기 때문에
 * 탐색 알고리즘을 나중에 바꿔도(헝가리안 등) 이 테스트는 그대로 유효하다.
 *
 * 서비스는 동점 처리를 위해 rand.nextDouble() * 0.001 의 미세 노이즈를 더하므로,
 * 테스트 점수는 항상 10 단위로 벌려 노이즈가 순위를 뒤집지 못하게 한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SeatAssignmentServiceTest {

    private static final Long EVENT = 1L;
    private static final long FEMALE_ID_BASE = 100L;

    @Mock private SeatAssignmentRepository seatRepo;
    @Mock private CustomerRepository customerRepo;
    @Mock private PreferenceRepository preferenceRepo;

    /** 테스트가 지정한 원점수 표. {@link #setUpScoreRound} 가 채운다. */
    private int[][] scoreTable;

    /**
     * 점수 계산기는 목이 아니라 테스트용 서브클래스로 둔다.
     * Mockito 의 inline mock maker 는 JDK 에 따라 구체 클래스를 계측하지 못하는 일이 있어
     * (Java 25 에서 실제로 깨졌다) 목으로 두면 실행 환경에 따라 테스트가 통째로 무너진다.
     * 여기서 필요한 건 "지정한 점수를 그대로 돌려주는 것" 뿐이라 상속으로 충분하다.
     */
    private final MatchScoreService scoreService = new MatchScoreService(null) {
        @Override
        public int totalScore(Customer a, Customer b) {
            return scoreTable[(int) (a.getId() - 1)][(int) (b.getId() - FEMALE_ID_BASE - 1)];
        }
    };

    private SeatAssignmentService service;

    @BeforeEach
    void assemble() {
        service = new SeatAssignmentService(seatRepo, customerRepo, preferenceRepo, scoreService);
    }

    // ------------------------------------------------------------------
    // 1차: 자리 번호 그대로 배치 + 성별 슬롯 반전
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("1차 배치")
    class Round1 {

        @Test
        @DisplayName("자리 1~6은 여성이 slot 0(위), 남성이 slot 1(아래)")
        void 자리1to6_슬롯_반전() {
            Customer m = male(1, 3);
            Customer f = female(1, 3);
            when(customerRepo.findByEventIdAndStatusOrderByCreatedAtAsc(EVENT, CustomerStatus.ACTIVE))
                    .thenReturn(List.of(m, f));
            stubSaveAll();

            List<SeatAssignment> result = service.assignRound1(EVENT);

            assertThat(slotOf(result, m.getId())).isEqualTo(1);
            assertThat(slotOf(result, f.getId())).isEqualTo(0);
        }

        @Test
        @DisplayName("자리 7~9는 반대로 남성이 slot 0(위), 여성이 slot 1(아래)")
        void 자리7to9_슬롯_반전() {
            Customer m = male(1, 8);
            Customer f = female(1, 8);
            when(customerRepo.findByEventIdAndStatusOrderByCreatedAtAsc(EVENT, CustomerStatus.ACTIVE))
                    .thenReturn(List.of(m, f));
            stubSaveAll();

            List<SeatAssignment> result = service.assignRound1(EVENT);

            assertThat(slotOf(result, m.getId())).isEqualTo(0);
            assertThat(slotOf(result, f.getId())).isEqualTo(1);
        }

        @Test
        @DisplayName("자리 번호를 입력하지 않은 손님은 배치에서 제외된다")
        void 자리번호_없으면_제외() {
            Customer seated = male(1, 2);
            Customer unseated = male(2, null);
            when(customerRepo.findByEventIdAndStatusOrderByCreatedAtAsc(EVENT, CustomerStatus.ACTIVE))
                    .thenReturn(List.of(seated, unseated));
            stubSaveAll();

            List<SeatAssignment> result = service.assignRound1(EVENT);

            assertThat(customerIds(result)).containsExactly(1L);
        }

        @Test
        @DisplayName("같은 자리·같은 성별이 겹치면 먼저 등록한 손님이 이긴다")
        void 자리_중복이면_선착순() {
            Customer first = male(1, 5);
            Customer second = male(2, 5);
            when(customerRepo.findByEventIdAndStatusOrderByCreatedAtAsc(EVENT, CustomerStatus.ACTIVE))
                    .thenReturn(List.of(first, second));
            stubSaveAll();

            List<SeatAssignment> result = service.assignRound1(EVENT);

            assertThat(customerIds(result)).containsExactly(1L);
        }
    }

    // ------------------------------------------------------------------
    // 2차: 점수 최대화 — 최적해 보장이 핵심
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("2차 배치 (점수 최대화)")
    class Round2 {

        @Test
        @DisplayName("무작위 점수표 100개에 대해 항상 브루트포스 최적해와 같은 총점을 낸다")
        void 항상_최적해() {
            Random rnd = new Random(20260909L);
            for (int trial = 0; trial < 100; trial++) {
                int n = 2 + rnd.nextInt(5);           // 2~6명
                int[][] raw = randomScores(rnd, n, n);
                setUpScoreRound(n, n, raw, List.of());

                List<SeatAssignment> result = service.assignRound2(EVENT);

                assertThat(pairScoreSum(result, raw))
                        .as("trial %d (n=%d) 총점이 최적이 아님", trial, n)
                        .isEqualTo(bestSum(raw, n, n, true));
            }
        }

        @Test
        @DisplayName("점수가 낮아도 이전 라운드에 앉았던 자리는 피한다")
        void 이전_자리_회피() {
            // 남1은 1차에서 자리 1(여1 옆)에 앉았다. 점수는 여1 쪽이 압도적으로 높지만
            // -1e15 페널티 때문에 자리 2(여2)로 밀려나야 한다.
            int[][] raw = { {90, 10}, {10, 10} };
            setUpScoreRound(2, 2, raw, List.of(seat(1, 1, 1, Gender.MALE, 1L)));

            List<SeatAssignment> result = service.assignRound2(EVENT);

            assertThat(seatOf(result, 1L)).isEqualTo(2);
        }
    }

    // ------------------------------------------------------------------
    // 3차: 점수 최소화 (일부러 안 맞는 상대와)
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("3차 배치 (점수 최소화)")
    class Round3 {

        @Test
        @DisplayName("무작위 점수표 100개에 대해 항상 총점이 최소인 배치를 낸다")
        void 항상_최악해() {
            Random rnd = new Random(4242L);
            for (int trial = 0; trial < 100; trial++) {
                int n = 2 + rnd.nextInt(5);
                int[][] raw = randomScores(rnd, n, n);
                setUpScoreRound(n, n, raw, List.of());

                List<SeatAssignment> result = service.assignRound3(EVENT);

                assertThat(pairScoreSum(result, raw))
                        .as("trial %d (n=%d) 총점이 최소가 아님", trial, n)
                        .isEqualTo(bestSum(raw, n, n, false));
            }
        }
    }

    // ------------------------------------------------------------------
    // 4차: 지목 우선순위
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("4차 배치 (지목 우선순위)")
    class Round4 {

        @Test
        @DisplayName("쌍방 1순위가 단방 지목보다 먼저 옆자리를 가져간다")
        void 쌍방1순위_우선() {
            // 남1↔여1 쌍방 1순위. 남1→여2 는 단방 1순위.
            setUpRound4(2, 2, List.of(
                    pref(1L, FEMALE_ID_BASE + 1, FEMALE_ID_BASE + 2, null),
                    pref(FEMALE_ID_BASE + 1, 1L, null, null)
            ), List.of());

            List<SeatAssignment> result = service.assignRound4(EVENT);

            assertThat(seatOf(result, 1L))
                    .as("남1은 쌍방 1순위인 여1의 자리에 앉아야 한다")
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("단방 지목이 미지목보다 먼저 옆자리를 가져간다")
        void 단방지목_우선() {
            setUpRound4(2, 2, List.of(
                    pref(1L, FEMALE_ID_BASE + 2, null, null)
            ), List.of());

            List<SeatAssignment> result = service.assignRound4(EVENT);

            assertThat(seatOf(result, 1L)).isEqualTo(2);
        }

        @Test
        @DisplayName("쌍방 1순위는 이전 라운드에 앉았던 자리라도 무시하고 붙인다")
        void 쌍방1순위는_자리회피_무시() {
            // 남1은 1·2·3차에서 이미 자리 1에 앉았지만, 여1과 쌍방 1순위이므로 또 자리 1.
            setUpRound4(2, 2, List.of(
                            pref(1L, FEMALE_ID_BASE + 1, null, null),
                            pref(FEMALE_ID_BASE + 1, 1L, null, null)
                    ),
                    List.of(seat(1, 1, 1, Gender.MALE, 1L)));

            List<SeatAssignment> result = service.assignRound4(EVENT);

            assertThat(seatOf(result, 1L)).isEqualTo(1);
        }

        @Test
        @DisplayName("단방 지목도 이전에 앉았던 자리를 무시하고 붙는다")
        void 단방지목도_자리회피_무시() {
            // 남1→여1 단방 1순위. 자리 1은 이미 앉았던 자리지만 지목을 따라간다.
            setUpRound4(2, 2, List.of(
                            pref(1L, FEMALE_ID_BASE + 1, null, null)
                    ),
                    List.of(seat(1, 1, 1, Gender.MALE, 1L)));

            List<SeatAssignment> result = service.assignRound4(EVENT);

            assertThat(seatOf(result, 1L)).isEqualTo(1);
        }

        @Test
        @DisplayName("3순위 지목도 이전에 앉았던 자리를 무시하고 붙는다")
        void 삼순위도_자리회피_무시() {
            setUpRound4(2, 2, List.of(
                            pref(1L, null, null, FEMALE_ID_BASE + 1)
                    ),
                    List.of(seat(1, 1, 1, Gender.MALE, 1L)));

            List<SeatAssignment> result = service.assignRound4(EVENT);

            assertThat(seatOf(result, 1L)).isEqualTo(1);
        }

        @Test
        @DisplayName("지목이 없는 쌍끼리는 이전에 앉았던 자리를 피한다")
        void 미지목은_자리회피() {
            // 아무도 지목하지 않았다. 자리 회피는 이때만 쓰는 기준이다.
            setUpRound4(2, 2, List.of(), List.of(seat(1, 1, 1, Gender.MALE, 1L)));

            List<SeatAssignment> result = service.assignRound4(EVENT);

            assertThat(seatOf(result, 1L)).isEqualTo(2);
        }

        @Test
        @DisplayName("자리 회피 때문에 상위 순위 쌍이 하위 순위에 밀리지 않는다")
        void 우선순위가_자리회피보다_우선() {
            // 남1↔여1 은 쌍방 1순위(최상위). 다만 남1 은 1·2·3차에서 자리 1 에 앉은 적이 있다.
            // 남1→여2 는 단방 1순위라 자리 2 로 가면 회피 조건은 만족하지만 순위가 낮다.
            // 자리 회피를 이유로 쌍방 1순위를 깨면 안 된다.
            setUpRound4(2, 2, List.of(
                            pref(1L, FEMALE_ID_BASE + 1, FEMALE_ID_BASE + 2, null),
                            pref(FEMALE_ID_BASE + 1, 1L, null, null)
                    ),
                    List.of(seat(1, 1, 1, Gender.MALE, 1L)));

            List<SeatAssignment> result = service.assignRound4(EVENT);

            assertThat(seatOf(result, 1L))
                    .as("쌍방 1순위인 여1(자리 1)과 붙어야 한다")
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("후순위 쌍이 여럿이어도 상위 순위 쌍을 깨지 않는다")
        void 상위순위가_후순위_여럿을_이긴다() {
            // 남1↔여1 쌍방 1순위(최상위 등급).
            // 남2~남4 는 여2~여4 와 단방 지목. 남1 을 여2 쪽으로 밀면 단방 쌍이 하나 늘지만,
            // 그 대가로 최상위 쌍이 깨진다. 등급이 한 단계 높으면 아무리 모아도 못 이겨야 한다.
            setUpRound4(4, 4, List.of(
                    pref(1L, FEMALE_ID_BASE + 1, FEMALE_ID_BASE + 2, null),
                    pref(FEMALE_ID_BASE + 1, 1L, null, null),
                    pref(2L, FEMALE_ID_BASE + 2, null, null),
                    pref(3L, FEMALE_ID_BASE + 3, null, null),
                    pref(4L, FEMALE_ID_BASE + 4, null, null)
            ), List.of());

            List<SeatAssignment> result = service.assignRound4(EVENT);

            assertThat(seatOf(result, 1L)).isEqualTo(1);
        }
    }

    // ------------------------------------------------------------------
    // 인원 불균형 — 실제 이벤트에서 노쇼가 나면 바로 만나는 경로
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("남녀 인원이 다를 때")
    class Unbalanced {

        @Test
        @DisplayName("남성이 더 많아도 한 자리에 두 명이 겹치지 않는다")
        void 중복_배치_없음() {
            int[][] raw = { {50, 50}, {50, 50}, {50, 50} };   // 남 3, 여 2
            setUpScoreRound(3, 2, raw, List.of());

            List<SeatAssignment> result = service.assignRound2(EVENT);

            Set<String> cells = new HashSet<>();
            for (SeatAssignment s : result) {
                assertThat(cells.add(s.getSeatNumber() + ":" + s.getSlotIndex()))
                        .as("자리 %d slot %d 에 두 명이 배치됨", s.getSeatNumber(), s.getSlotIndex())
                        .isTrue();
            }
        }

        @Test
        @DisplayName("남성이 더 많으면 궁합이 좋은 남성이 옆자리를 가져간다")
        void 남성_초과시_최적_남성이_선택된다() {
            // 여성은 1명뿐. 남3이 압도적으로 잘 맞으므로 남3이 그 자리에 앉아야 한다.
            int[][] raw = { {10}, {20}, {90} };   // 남 3, 여 1
            setUpScoreRound(3, 1, raw, List.of());

            List<SeatAssignment> result = service.assignRound2(EVENT);

            assertThat(pairScoreSum(result, raw))
                    .as("여성 옆자리는 가장 궁합이 좋은 남성의 몫이어야 한다")
                    .isEqualTo(90);
        }

        @Test
        @DisplayName("여성이 더 많아도 최적해를 낸다")
        void 여성_초과() {
            int[][] raw = { {10, 90, 30} };   // 남 1, 여 3
            setUpScoreRound(1, 3, raw, List.of());

            List<SeatAssignment> result = service.assignRound2(EVENT);

            assertThat(pairScoreSum(result, raw)).isEqualTo(90);
        }

        @Test
        @DisplayName("인원이 어긋난 무작위 점수표 100개에 대해서도 항상 최적해를 낸다")
        void 인원_불균형_항상_최적해() {
            Random rnd = new Random(777L);
            for (int trial = 0; trial < 100; trial++) {
                int nMales = 1 + rnd.nextInt(6);      // 1~6
                int nFemales = 1 + rnd.nextInt(6);    // 1~6
                int[][] raw = randomScores(rnd, nMales, nFemales);
                setUpScoreRound(nMales, nFemales, raw, List.of());

                List<SeatAssignment> result = service.assignRound2(EVENT);

                assertThat(pairScoreSum(result, raw))
                        .as("trial %d (남 %d / 여 %d) 총점이 최적이 아님", trial, nMales, nFemales)
                        .isEqualTo(bestSum(raw, nMales, nFemales, true));
            }
        }

        @Test
        @DisplayName("참가자가 아무도 없으면 빈 결과를 낸다")
        void 참가자_없음() {
            setUpScoreRound(0, 0, new int[0][0], List.of());

            assertThat(service.assignRound2(EVENT)).isEmpty();
        }
    }

    // ==================================================================
    // 헬퍼
    // ==================================================================

    /** 남성 i(1-base) → 여성 j(1-base) 원점수 표를 세팅하고 1차 여성 자리를 자리 j 에 놓는다. */
    private void setUpScoreRound(int nMales, int nFemales, int[][] raw, List<SeatAssignment> round1Males) {
        List<Customer> males = new ArrayList<>();
        for (int i = 1; i <= nMales; i++) males.add(male(i, null));
        List<Customer> females = new ArrayList<>();
        for (int j = 1; j <= nFemales; j++) females.add(female(j, j));

        List<SeatAssignment> round1 = new ArrayList<>(round1Males);
        for (int j = 1; j <= nFemales; j++) {
            round1.add(seat(1, j, femaleSlot(j), Gender.FEMALE, FEMALE_ID_BASE + j));
        }

        // 루프 안에서 반복 세팅하므로 doReturn/doAnswer 를 쓴다.
        // when(mock.method(...)) 형태는 스터빙하는 순간 직전 반복의 answer 를 null 인자로 실행해버린다.
        doReturn(males).when(customerRepo)
                .findByEventIdAndGenderAndStatusOrderByCreatedAtAsc(EVENT, Gender.MALE, CustomerStatus.ACTIVE);
        doReturn(round1).when(seatRepo).findByEventIdAndRound(EVENT, 1);
        doReturn(List.of()).when(seatRepo).findByEventIdAndRound(EVENT, 2);
        doReturn(List.of()).when(seatRepo).findByEventIdAndRound(EVENT, 3);
        doReturn(females).when(customerRepo).findAllById(any());
        scoreTable = raw;
        stubSaveAll();
    }

    private void setUpRound4(int nMales, int nFemales, List<Preference> prefs, List<SeatAssignment> priorMales) {
        int[][] zero = new int[Math.max(nMales, 1)][Math.max(nFemales, 1)];
        setUpScoreRound(nMales, nFemales, zero, priorMales);
        // 4차는 1·2·3차 자리를 모두 회피 대상으로 본다.
        doReturn(priorMales).when(seatRepo).findByEventIdAndRound(EVENT, 2);
        doReturn(priorMales).when(seatRepo).findByEventIdAndRound(EVENT, 3);
        doReturn(prefs).when(preferenceRepo).findByEventIdOrderByCreatedAtAsc(EVENT);
    }

    @SuppressWarnings("unchecked")
    private void stubSaveAll() {
        doAnswer(inv -> new ArrayList<>((List<SeatAssignment>) inv.getArgument(0)))
                .when(seatRepo).saveAll(any());
    }

    private Customer male(long id, Integer seatNumber) {
        return buildCustomer(id, Gender.MALE, seatNumber);
    }

    /** 여성 id 는 남성과 겹치지 않도록 100 을 더한다. */
    private Customer female(long ordinal, Integer seatNumber) {
        return buildCustomer(FEMALE_ID_BASE + ordinal, Gender.FEMALE, seatNumber);
    }

    private Customer buildCustomer(long id, Gender gender, Integer seatNumber) {
        return Customer.builder()
                .id(id).eventId(EVENT)
                .nickname("손님" + id).realName("실명" + id).phone("010" + id)
                .gender(gender).mbti("INFP").valuesAnswers("AAAAA")
                .seatNumber(seatNumber).status(CustomerStatus.ACTIVE)
                .createdAt(LocalDateTime.of(2026, 9, 9, 20, 0).plusSeconds(id))
                .build();
    }

    private SeatAssignment seat(int round, int seatNumber, int slot, Gender gender, long customerId) {
        return SeatAssignment.builder()
                .eventId(EVENT).round(round)
                .seatNumber(seatNumber).slotIndex(slot)
                .gender(gender).customerId(customerId)
                .build();
    }

    private Preference pref(long voterId, Long first, Long second, Long third) {
        return Preference.builder()
                .eventId(EVENT).voterId(voterId)
                .firstChoiceId(first).secondChoiceId(second).thirdChoiceId(third)
                .createdAt(LocalDateTime.of(2026, 9, 9, 21, 0))
                .build();
    }

    private int femaleSlot(int seat) { return (seat >= 1 && seat <= 6) ? 0 : 1; }

    private int[][] randomScores(Random rnd, int nMales, int nFemales) {
        int[][] raw = new int[nMales][nFemales];
        for (int i = 0; i < nMales; i++) {
            for (int j = 0; j < nFemales; j++) raw[i][j] = rnd.nextInt(11) * 10;   // 0,10,...,100
        }
        return raw;
    }

    /** 배치 결과에서 남녀가 같은 자리에 앉은 쌍의 원점수를 모두 더한다. */
    private int pairScoreSum(List<SeatAssignment> result, int[][] raw) {
        Map<Integer, Long> maleBySeat = new HashMap<>();
        Map<Integer, Long> femaleBySeat = new HashMap<>();
        for (SeatAssignment s : result) {
            if (s.getGender() == Gender.MALE) maleBySeat.put(s.getSeatNumber(), s.getCustomerId());
            else femaleBySeat.put(s.getSeatNumber(), s.getCustomerId());
        }
        int sum = 0;
        for (Map.Entry<Integer, Long> e : maleBySeat.entrySet()) {
            Long fId = femaleBySeat.get(e.getKey());
            if (fId == null) continue;
            sum += raw[(int) (e.getValue() - 1)][(int) (fId - FEMALE_ID_BASE - 1)];
        }
        return sum;
    }

    /**
     * 독립 오라클: 남성→여성 단사 대응 중 짝 수가 최대인 것들만 후보로 두고
     * 그 안에서 총점의 최대(또는 최소)를 구한다. 서비스 구현을 참조하지 않는다.
     */
    private int bestSum(int[][] raw, int nMales, int nFemales, boolean max) {
        int[] best = { max ? Integer.MIN_VALUE : Integer.MAX_VALUE };
        search(raw, nMales, nFemales, 0, new boolean[nFemales], 0, Math.min(nMales, nFemales), 0, best, max);
        return best[0] == Integer.MIN_VALUE || best[0] == Integer.MAX_VALUE ? 0 : best[0];
    }

    private void search(int[][] raw, int nMales, int nFemales, int i, boolean[] used,
                        int matched, int needed, int sum, int[] best, boolean max) {
        if (i == nMales) {
            if (matched != needed) return;                      // 짝 수가 최대인 배치만 인정
            best[0] = max ? Math.max(best[0], sum) : Math.min(best[0], sum);
            return;
        }
        for (int j = 0; j < nFemales; j++) {
            if (used[j]) continue;
            used[j] = true;
            search(raw, nMales, nFemales, i + 1, used, matched + 1, needed, sum + raw[i][j], best, max);
            used[j] = false;
        }
        search(raw, nMales, nFemales, i + 1, used, matched, needed, sum, best, max);   // 이 남성은 미배치
    }

    private Integer slotOf(List<SeatAssignment> result, Long customerId) {
        return result.stream().filter(s -> s.getCustomerId().equals(customerId))
                .map(SeatAssignment::getSlotIndex).findFirst().orElse(null);
    }

    private Integer seatOf(List<SeatAssignment> result, Long customerId) {
        return result.stream().filter(s -> s.getCustomerId().equals(customerId))
                .map(SeatAssignment::getSeatNumber).findFirst().orElse(null);
    }

    private List<Long> customerIds(List<SeatAssignment> result) {
        return result.stream().map(SeatAssignment::getCustomerId).toList();
    }
}
