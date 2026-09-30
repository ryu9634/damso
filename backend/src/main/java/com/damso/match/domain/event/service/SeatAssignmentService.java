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

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 자리 배치.
 *  - 1차: 손님이 입력한 자리 번호로 배치
 *  - 2차: 매칭 점수 높은 쌍부터 옆자리. 동점은 랜덤
 *  - 3차: 반대로 매칭 점수가 가장 낮은 쌍끼리 옆자리. (점수는 점수일 뿐이라 직접 만나보면 다르다)
 *  - 4차: 쌍방 1순위 > 1·2순위 교차/쌍방 2순위 > 단방 지목 > 미지목 순으로 옆자리
 *  - 자리 1~6: slot 0 = 여, slot 1 = 남 (UI 시각상 위=여, 밑=남)
 *  - 자리 7~9: slot 0 = 남, slot 1 = 여 (UI 시각상 위=남, 밑=여)
 *  - 모든 라운드는 자동 배치 후 운영자가 수동 보정 가능
 */
@Service
@RequiredArgsConstructor
public class SeatAssignmentService {

    private final SeatAssignmentRepository seatRepo;
    private final CustomerRepository customerRepo;
    private final PreferenceRepository preferenceRepo;
    private final MatchScoreService scoreService;

    public List<SeatAssignment> getAssignments(Long eventId, int round) {
        return seatRepo.findByEventIdAndRoundOrderBySeatNumberAsc(eventId, round);
    }

    /**
     * 4차 지목 점수의 밑.
     *
     * <p>등급이 한 단계 높으면 낮은 등급을 <b>아무리 모아도</b> 못 이겨야 한다
     * ("후순위가 여러 쌍이어도 우선순위가 이긴다"). 한 배치에 들어가는 쌍은 자리 수만큼,
     * 최대 9쌍이므로 밑이 9보다 크면 그 성질이 보장된다. 여유를 둬 16으로 잡는다.
     *
     * <p>16^9 ≈ 6.9e10 이고 아홉 쌍을 더해도 7e11 이라 double 이 정수를 정확히 담는
     * 범위(9e15) 안에 있다. 밑을 더 키우면 낮은 등급의 차이가 반올림으로 사라진다.
     */
    private static final double PICK_RANK_BASE = 16.0;

    /**
     * 4차 지목 우선순위 등급. 높을수록 먼저 옆자리를 가져간다.
     *
     * <pre>
     *   9 …… 5   쌍방 지목 — 강도 합이 큰 순 (9=1·1순위, 5=3·3순위)
     *   4 …… 2   단방 지목 — 1순위(4) / 2순위(3) / 3순위(2)
     *   1        미지목
     *   0        미지목이면서 이전 회차에 앉았던 자리
     * </pre>
     *
     * <p>지목으로 이어진 쌍(2 이상)에는 이전 자리 회피를 걸지 않는다. 순위를 따라 붙는 것이
     * 같은 자리를 피하는 것보다 우선이기 때문이다. 자리 회피는 붙일 이유가 없는 쌍끼리
     * 누구를 옆에 앉힐지 고를 때만 쓰는 기준이다.
     */
    private static int pickRank(int mw, int fw, boolean seatRepeated) {
        if (mw > 0 && fw > 0) return 3 + mw + fw;          // 합 2~6 → 5~9
        if (mw > 0 || fw > 0) return 1 + Math.max(mw, fw); // 1~3   → 2~4
        return seatRepeated ? 0 : 1;
    }

    /** 자리별 성별 슬롯 인덱스. 1~6은 뒤집힘. */
    private int maleSlot(int seat) { return (seat >= 1 && seat <= 6) ? 1 : 0; }
    private int femaleSlot(int seat) { return (seat >= 1 && seat <= 6) ? 0 : 1; }

    /** 1차: 손님이 입력한 자리 번호 기준 배치. 자리당 남(slot 0) / 여(slot 1) 한 명씩. */
    @Transactional
    public List<SeatAssignment> assignRound1(Long eventId) {
        seatRepo.deleteByEventIdAndRound(eventId, 1);
        seatRepo.flush();

        var status = com.damso.match.domain.event.entity.CustomerStatus.ACTIVE;
        List<Customer> all = customerRepo.findByEventIdAndStatusOrderByCreatedAtAsc(eventId, status);

        Map<Integer, Customer> seatMale = new HashMap<>();
        Map<Integer, Customer> seatFemale = new HashMap<>();
        for (Customer c : all) {
            if (c.getSeatNumber() == null) continue;
            if (c.getGender() == Gender.MALE) seatMale.putIfAbsent(c.getSeatNumber(), c);
            else seatFemale.putIfAbsent(c.getSeatNumber(), c);
        }

        List<SeatAssignment> result = new ArrayList<>();
        for (int seat = 1; seat <= 9; seat++) {
            Customer m = seatMale.get(seat);
            Customer f = seatFemale.get(seat);
            if (m != null) result.add(build(eventId, 1, m, seat, maleSlot(seat)));
            if (f != null) result.add(build(eventId, 1, f, seat, femaleSlot(seat)));
        }
        return seatRepo.saveAll(result);
    }

    /** 2차: 여성은 1차 위치 고정. 남성만 매칭 점수 최대화로 재배치. 이전 자리 회피. 동점은 랜덤. */
    @Transactional
    public List<SeatAssignment> assignRound2(Long eventId) {
        return assignByScore(eventId, 2, false, 1);
    }

    /**
     * 3차: 2차와 정반대로, 매칭 점수가 가장 낮게 나온 상대와 앉힌다.
     * 여성 자리는 그대로 두고 남성만 옮기며, 1·2차에 앉았던 자리는 피한다.
     */
    @Transactional
    public List<SeatAssignment> assignRound3(Long eventId) {
        return assignByScore(eventId, 3, true, 1, 2);
    }

    /**
     * 매칭 점수로 남성을 재배치한다. 여성은 1차 자리 그대로.
     *
     * @param worst        true 면 점수가 낮은 쌍끼리(가장 안 맞는 상대), false 면 높은 쌍끼리
     * @param avoidRounds  같은 자리에 다시 앉지 않도록 피할 이전 라운드들
     */
    private List<SeatAssignment> assignByScore(Long eventId, int round, boolean worst, int... avoidRounds) {
        seatRepo.deleteByEventIdAndRound(eventId, round);
        seatRepo.flush();

        var status = com.damso.match.domain.event.entity.CustomerStatus.ACTIVE;
        List<Customer> males = customerRepo.findByEventIdAndGenderAndStatusOrderByCreatedAtAsc(eventId, Gender.MALE, status);

        List<SeatAssignment> r1Females = seatRepo.findByEventIdAndRound(eventId, 1).stream()
                .filter(s -> s.getGender() == Gender.FEMALE).toList();
        if (r1Females.isEmpty() && males.isEmpty()) return List.of();

        Map<Long, Customer> femaleById = new HashMap<>();
        customerRepo.findAllById(r1Females.stream().map(SeatAssignment::getCustomerId).toList())
                .forEach(c -> femaleById.put(c.getId(), c));

        Map<Long, Set<Integer>> maleForbiddenSeats = collectMaleSeatHistory(eventId, avoidRounds);

        Random rand = new Random();
        double[][] score = new double[males.size()][r1Females.size()];
        for (int i = 0; i < males.size(); i++) {
            Set<Integer> forbidden = maleForbiddenSeats.getOrDefault(males.get(i).getId(), Set.of());
            for (int j = 0; j < r1Females.size(); j++) {
                Customer f = femaleById.get(r1Females.get(j).getCustomerId());
                double raw = (f == null) ? 0 : scoreService.totalScore(males.get(i), f);
                // 탐색은 늘 '합이 큰 쪽'을 고르므로, 안 맞는 쌍을 원할 때는 부호만 뒤집는다.
                double s = worst ? -raw : raw;
                s += rand.nextDouble() * 0.001;
                if (forbidden.contains(r1Females.get(j).getSeatNumber())) s -= 1e15;
                score[i][j] = s;
            }
        }

        int[] pairing = bestAssignment(males.size(), r1Females.size(), score);
        return saveFemaleFixed(eventId, round, males, r1Females, pairing);
    }

    /** 특정 라운드(들)에서 남성이 앉았던 자리 번호 집합. */
    private Map<Long, Set<Integer>> collectMaleSeatHistory(Long eventId, int... rounds) {
        Map<Long, Set<Integer>> map = new HashMap<>();
        for (int r : rounds) {
            for (SeatAssignment s : seatRepo.findByEventIdAndRound(eventId, r)) {
                if (s.getGender() == Gender.MALE) {
                    map.computeIfAbsent(s.getCustomerId(), k -> new HashSet<>()).add(s.getSeatNumber());
                }
            }
        }
        return map;
    }

    /**
     * 4차: 지목 우선순위 페어링.
     *  티어 4: 쌍방 1순위 (앞선 자리와 무관하게 무조건 옆자리)
     *  티어 3: 1·2순위 교차 또는 쌍방 2순위
     *  티어 2: 단방 지목
     *  티어 1: 미지목
     *  (티어 3 이하는 1·2·3차에서 앉았던 자리를 회피)
     */
    @Transactional
    public List<SeatAssignment> assignRound4(Long eventId) {
        seatRepo.deleteByEventIdAndRound(eventId, 4);
        seatRepo.flush();

        var status = com.damso.match.domain.event.entity.CustomerStatus.ACTIVE;
        List<Customer> males = customerRepo.findByEventIdAndGenderAndStatusOrderByCreatedAtAsc(eventId, Gender.MALE, status);

        // 1차 여성 자리 그대로 가져오기
        List<SeatAssignment> r1Females = seatRepo.findByEventIdAndRound(eventId, 1).stream()
                .filter(s -> s.getGender() == Gender.FEMALE).toList();
        if (r1Females.isEmpty() && males.isEmpty()) return List.of();

        List<Preference> prefs = preferenceRepo.findByEventIdOrderByCreatedAtAsc(eventId);
        // 지목 가중치: voter -> (target -> 3=1순위, 2=2순위, 1=3순위)
        Map<Long, Map<Long, Integer>> pickWeight = new HashMap<>();
        for (Preference p : prefs) {
            Map<Long, Integer> w = pickWeight.computeIfAbsent(p.getVoterId(), k -> new HashMap<>());
            if (p.getFirstChoiceId() != null) w.put(p.getFirstChoiceId(), 3);
            if (p.getSecondChoiceId() != null) w.put(p.getSecondChoiceId(), 2);
            if (p.getThirdChoiceId() != null) w.put(p.getThirdChoiceId(), 1);
        }

        // 이전 라운드(1·2·3차) 남성 자리 → 같은 자리 회피
        Map<Long, Set<Integer>> maleForbiddenSeats = collectMaleSeatHistory(eventId, 1, 2, 3);

        Random rand = new Random();
        double[][] score = new double[males.size()][r1Females.size()];
        for (int i = 0; i < males.size(); i++) {
            Long mId = males.get(i).getId();
            Map<Long, Integer> mPicks = pickWeight.getOrDefault(mId, Map.of());
            Set<Integer> forbidden = maleForbiddenSeats.getOrDefault(mId, Set.of());
            for (int j = 0; j < r1Females.size(); j++) {
                Long fId = r1Females.get(j).getCustomerId();
                Map<Long, Integer> fPicks = pickWeight.getOrDefault(fId, Map.of());

                int mw = mPicks.getOrDefault(fId, 0);   // 남→여 지목 강도 (3=1순위, 2=2순위, 1=3순위, 0=미지목)
                int fw = fPicks.getOrDefault(mId, 0);   // 여→남 지목 강도

                boolean seatRepeated = forbidden.contains(r1Females.get(j).getSeatNumber());
                score[i][j] = Math.pow(PICK_RANK_BASE, pickRank(mw, fw, seatRepeated)) + rand.nextDouble();
            }
        }

        int[] pairing = bestAssignment(males.size(), r1Females.size(), score);
        return saveFemaleFixed(eventId, 4, males, r1Females, pairing);
    }

    /**
     * 여성 자리 고정 + 남성 페어링 결과를 같은 자리에 배치.
     * 페어 매칭된 남성: 해당 여성 자리의 남성 슬롯에 배치
     * 미페어 남성: 여성이 없는 빈 자리(자리 1~9 중)에 단독 배치
     */
    private List<SeatAssignment> saveFemaleFixed(Long eventId, int round,
                                                  List<Customer> males,
                                                  List<SeatAssignment> r1Females,
                                                  int[] pairing) {
        List<SeatAssignment> result = new ArrayList<>();
        Set<Integer> femaleSeats = new HashSet<>(); // 여성이 이미 들어간 자리 번호

        // 여성: 1차 위치 그대로 복사
        for (SeatAssignment f : r1Females) {
            result.add(SeatAssignment.builder()
                    .eventId(eventId).round(round)
                    .seatNumber(f.getSeatNumber()).slotIndex(f.getSlotIndex())
                    .gender(Gender.FEMALE).customerId(f.getCustomerId())
                    .build());
            femaleSeats.add(f.getSeatNumber());
        }

        // 남성: 페어링된 여성과 같은 자리의 남성 슬롯에 배치
        Set<Integer> assignedMales = new HashSet<>();
        for (int i = 0; i < pairing.length; i++) {
            int j = pairing[i];
            if (j < 0) continue;
            int seatNum = r1Females.get(j).getSeatNumber();
            result.add(build(eventId, round, males.get(i), seatNum, maleSlot(seatNum)));
            assignedMales.add(i);
        }

        // 미페어 남성: 여성이 없는 자리에 단독 배치
        int seat = 1;
        for (int i = 0; i < males.size(); i++) {
            if (assignedMales.contains(i)) continue;
            while (seat <= 9 && femaleSeats.contains(seat)) seat++;
            if (seat > 9) break;
            result.add(build(eventId, round, males.get(i), seat, maleSlot(seat)));
            seat++;
        }
        return seatRepo.saveAll(result);
    }

    private SeatAssignment build(Long eventId, int round, Customer c, int seatNumber, int slot) {
        return SeatAssignment.builder()
                .eventId(eventId).round(round)
                .seatNumber(seatNumber).slotIndex(slot)
                .gender(c.getGender()).customerId(c.getId())
                .build();
    }

    /** 브루트포스 순열로 최적 페어링 탐색. 반환: males[i] → females[result[i]]. 매칭 불가 = -1. */
    private int[] bestAssignment(int nMales, int nFemales, double[][] score) {
        int[] best = new int[nMales];
        Arrays.fill(best, -1);
        if (nMales == 0 || nFemales == 0) return best;

        // 남성이 여성보다 많으면 '짝 없음'을 뜻하는 가상 열을 채워 정사각으로 만든다.
        // 여성 수만큼만 순열을 돌리면 뒤쪽 남성은 점수와 무관하게 늘 미배치가 되어,
        // 궁합이 가장 좋은 남성이 옆자리를 놓치는 일이 생긴다.
        // 가상 열은 어떤 순열에서도 정확히 (nMales - nFemales) 개가 쓰이므로 총점 비교에는 영향이 없다.
        int n = Math.max(nMales, nFemales);
        int[] indices = new int[n];
        for (int i = 0; i < n; i++) indices[i] = i;
        double[] bestScore = { -Double.MAX_VALUE };
        permute(indices, n, nMales, nFemales, score, best, bestScore, new int[nMales]);
        return best;
    }

    private void permute(int[] arr, int size, int nMales, int nFemales, double[][] score,
                          int[] best, double[] bestScore, int[] tmp) {
        if (size == 1) {
            double sum = 0;
            for (int i = 0; i < nMales; i++) {
                int j = arr[i];
                if (j < nFemales) {
                    sum += score[i][j];
                    tmp[i] = j;
                } else {
                    tmp[i] = -1;   // 가상 열 — 짝 없이 빈 자리에 단독 배치된다.
                }
            }
            if (sum > bestScore[0]) {
                bestScore[0] = sum;
                System.arraycopy(tmp, 0, best, 0, nMales);
            }
            return;
        }
        for (int i = 0; i < size; i++) {
            permute(arr, size - 1, nMales, nFemales, score, best, bestScore, tmp);
            int j = (size % 2 == 0) ? i : 0;
            int t = arr[j]; arr[j] = arr[size - 1]; arr[size - 1] = t;
        }
    }

    /**
     * 수동 좌석 변경: 특정 자리의 특정 슬롯에 특정 손님 지정.
     *  - 한 손님은 한 라운드에 한 셀만
     *  - 같은 (seatNumber, slotIndex)에 다른 손님 있으면 교체
     */
    @Transactional
    public SeatAssignment manualAssign(Long eventId, int round, int seatNumber, int slotIndex, Long customerId) {
        Customer c = customerRepo.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("손님 없음: " + customerId));
        List<SeatAssignment> existing = seatRepo.findByEventIdAndRound(eventId, round);
        boolean removed = false;
        for (SeatAssignment s : existing) {
            if (s.getCustomerId().equals(customerId)) {
                seatRepo.delete(s);
                removed = true;
            } else if (s.getSeatNumber() == seatNumber && s.getSlotIndex() == slotIndex) {
                seatRepo.delete(s);
                removed = true;
            }
        }
        if (removed) seatRepo.flush();
        return seatRepo.save(SeatAssignment.builder()
                .eventId(eventId).round(round)
                .seatNumber(seatNumber).slotIndex(slotIndex)
                .gender(c.getGender()).customerId(customerId)
                .build());
    }

    @Transactional
    public void clearRound(Long eventId, int round) {
        seatRepo.deleteByEventIdAndRound(eventId, round);
    }

    @Transactional
    public void removeAssignment(Long eventId, int round, Long customerId) {
        seatRepo.findByEventIdAndRound(eventId, round).stream()
                .filter(s -> s.getCustomerId().equals(customerId))
                .forEach(seatRepo::delete);
    }
}