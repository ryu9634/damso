package com.damso.match.domain.dating.service;

import com.damso.match.api.admin.dating.dto.DatingMatchDto;
import com.damso.match.domain.dating.entity.DatingMatch;
import com.damso.match.domain.dating.entity.DatingMatchStage;
import com.damso.match.domain.dating.entity.DatingProfile;
import com.damso.match.domain.dating.repository.DatingMatchRepository;
import com.damso.match.domain.dating.repository.DatingProfileRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 관리자 매칭 워크플로우.
 *  - 쌍방 찜이 생기면 보드 카드(DatingMatch)를 자동 생성한다(목록 조회 시 동기화).
 *  - 관리자가 직접 페어를 만들 수도 있다(쌍방이 아니어도).
 *  - 찜을 취소해도 카드는 남는다. 진행 이력을 잃지 않기 위해서.
 */
@Service
@RequiredArgsConstructor
public class DatingMatchService {

    private final DatingMatchRepository matchRepo;
    private final DatingProfileRepository profileRepo;
    private final DatingService datingService;

    /** 쌍방 찜을 보드에 반영한 뒤 전체 카드를 돌려준다. */
    @Transactional
    public List<DatingMatchDto> listSynced() {
        Set<List<Long>> mutual = datingService.mutualPairs();

        // 새로 생긴 쌍방 → 카드 자동 생성
        for (List<Long> pair : mutual) {
            if (matchRepo.findByProfileAIdAndProfileBId(pair.get(0), pair.get(1)).isEmpty()) {
                matchRepo.save(DatingMatch.builder()
                        .profileAId(pair.get(0))
                        .profileBId(pair.get(1))
                        .stage(DatingMatchStage.NEW)
                        .fromMutual(true)
                        .createdAt(LocalDateTime.now())
                        .build());
            }
        }

        Map<Long, DatingProfile> byId = new HashMap<>();
        profileRepo.findAll().forEach(p -> byId.put(p.getId(), p));

        List<DatingMatch> all = new ArrayList<>(matchRepo.findAll());
        all.sort(Comparator.comparing(DatingMatch::getCreatedAt).reversed());

        List<DatingMatchDto> out = new ArrayList<>();
        for (DatingMatch m : all) {
            DatingProfile a = byId.get(m.getProfileAId());
            DatingProfile b = byId.get(m.getProfileBId());
            if (a == null || b == null) continue; // 완전 삭제된 회원의 잔여 카드
            out.add(new DatingMatchDto(
                    m.getId(),
                    a.getId(), a.getName(), a.getContact(), a.getBirthYear(),
                    b.getId(), b.getName(), b.getContact(), b.getBirthYear(),
                    DatingMatchStage.orDefault(m.getStage()),
                    m.getMemo(),
                    Boolean.TRUE.equals(m.getFromMutual()),
                    mutual.contains(List.of(m.getProfileAId(), m.getProfileBId())),
                    m.getCreatedAt(), m.getUpdatedAt()
            ));
        }
        return out;
    }

    /** 관리자가 직접 페어를 만든다 (쌍방 찜이 아니어도 가능). */
    @Transactional
    public void create(Long profileAId, Long profileBId) {
        if (profileAId == null || profileBId == null || profileAId.equals(profileBId)) {
            throw new IllegalArgumentException("서로 다른 두 회원을 선택해주세요.");
        }
        datingService.getRaw(profileAId);
        datingService.getRaw(profileBId);

        long a = Math.min(profileAId, profileBId);
        long b = Math.max(profileAId, profileBId);
        if (matchRepo.findByProfileAIdAndProfileBId(a, b).isPresent()) {
            throw new IllegalArgumentException("이미 등록된 매칭입니다.");
        }
        matchRepo.save(DatingMatch.builder()
                .profileAId(a).profileBId(b)
                .stage(DatingMatchStage.NEW)
                .fromMutual(false)
                .createdAt(LocalDateTime.now())
                .build());
    }

    @Transactional
    public void setStage(Long id, DatingMatchStage stage) {
        DatingMatch m = get(id);
        m.setStage(DatingMatchStage.orDefault(stage));
        m.setUpdatedAt(LocalDateTime.now());
        matchRepo.save(m);
    }

    @Transactional
    public void setMemo(Long id, String memo) {
        DatingMatch m = get(id);
        m.setMemo(memo == null || memo.isBlank() ? null : memo.trim());
        m.setUpdatedAt(LocalDateTime.now());
        matchRepo.save(m);
    }

    /**
     * 카드 삭제.
     * 아직 쌍방 찜 상태인 페어는 지워도 다음 조회 때 다시 생성되므로 막는다.
     * (진행을 끝내려면 '불발/성사' 단계로 옮길 것)
     */
    @Transactional
    public void delete(Long id) {
        DatingMatch m = get(id);
        if (datingService.mutualPairs().contains(List.of(m.getProfileAId(), m.getProfileBId()))) {
            throw new IllegalArgumentException(
                    "아직 쌍방 찜 상태라 삭제해도 다시 생성됩니다. '성사' 또는 '불발'로 옮겨주세요.");
        }
        matchRepo.deleteById(id);
    }

    private DatingMatch get(Long id) {
        return matchRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("매칭을 찾을 수 없습니다."));
    }
}