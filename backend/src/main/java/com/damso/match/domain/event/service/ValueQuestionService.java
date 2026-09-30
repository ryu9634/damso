package com.damso.match.domain.event.service;

import com.damso.match.domain.event.entity.ValueQuestion;
import com.damso.match.domain.event.repository.ValueQuestionRepository;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ValueQuestionService {

    private final ValueQuestionRepository repo;

    /** 기본 16문항. 비어있을 때만 시드. */
    private static final String[][] DEFAULTS = {
            {"갈등 해결 방식", "바로 대화로 푼다", "시간을 두고 정리한다"},
            {"애정표현 방식", "스킨십 위주", "말과 선물 위주"},
            {"\"사랑해\" 빈도", "자주 표현한다", "특별할 때만 한다"},
            {"여행 항공권 성향", "미리 예약", "즉흥적으로 떠난다"},
            {"여행지 취향", "휴양지", "도시/문화 탐방"},
            {"지각 시 사과 방식", "확실히 사과", "가볍게 넘어간다"},
            {"데이트 빈도", "주 2~3회 이상", "주 1회 정도"},
            {"첫 키스 성향", "빠르게 진도", "천천히 신뢰부터"},
            {"이상적인 연애관", "열정적이고 짧게", "안정적이고 길게"},
            {"술자리 연락 빈도", "자주 보고", "믿고 맡긴다"},
            {"선호 데이트 장소", "실내 카페/맛집", "야외/액티비티"},
            {"데이트 비용 분담", "더치페이", "한쪽이 더 낸다"},
            {"주말 휴식 성향", "집순이/집돌이", "밖에서 활동"},
            {"영화 장르 취향", "로맨스/드라마", "액션/스릴러"},
            {"이성 친구 기준", "상관없다", "연락 제한 필요"},
            {"연애 속도 성향", "빠르게 진전", "천천히 가까이"},
    };

    @PostConstruct
    @Transactional
    public void seedIfEmpty() {
        if (repo.count() > 0) return;
        List<ValueQuestion> list = new ArrayList<>();
        for (int i = 0; i < DEFAULTS.length; i++) {
            list.add(ValueQuestion.builder()
                    .sortOrder(i)
                    .label(DEFAULTS[i][0])
                    .optionA(DEFAULTS[i][1])
                    .optionB(DEFAULTS[i][2])
                    .build());
        }
        repo.saveAll(list);
    }

    public List<ValueQuestion> list() {
        return repo.findAllByOrderBySortOrderAsc();
    }

    public int count() {
        return (int) repo.count();
    }

    @Transactional
    public ValueQuestion create(String label, String optionA, String optionB) {
        int next = repo.findAll().size();
        return repo.save(ValueQuestion.builder()
                .sortOrder(next)
                .label(label)
                .optionA(optionA)
                .optionB(optionB)
                .build());
    }

    @Transactional
    public ValueQuestion update(Long id, String label, String optionA, String optionB) {
        ValueQuestion q = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("문항을 찾을 수 없습니다: " + id));
        q.setLabel(label);
        q.setOptionA(optionA);
        q.setOptionB(optionB);
        return q;
    }

    @Transactional
    public void delete(Long id) {
        repo.deleteById(id);
        // 삭제 후 sortOrder 재정렬
        List<ValueQuestion> remaining = repo.findAllByOrderBySortOrderAsc();
        for (int i = 0; i < remaining.size(); i++) {
            remaining.get(i).setSortOrder(i);
        }
    }

    /** id의 항목을 한 칸 위(또는 아래)로 이동. */
    @Transactional
    public List<ValueQuestion> move(Long id, boolean up) {
        List<ValueQuestion> all = repo.findAllByOrderBySortOrderAsc();
        int idx = -1;
        for (int i = 0; i < all.size(); i++) if (all.get(i).getId().equals(id)) { idx = i; break; }
        if (idx < 0) throw new IllegalArgumentException("문항을 찾을 수 없습니다: " + id);
        int swapWith = up ? idx - 1 : idx + 1;
        if (swapWith < 0 || swapWith >= all.size()) return all;
        ValueQuestion a = all.get(idx);
        ValueQuestion b = all.get(swapWith);
        int tmp = a.getSortOrder();
        a.setSortOrder(b.getSortOrder());
        b.setSortOrder(tmp);
        return repo.findAllByOrderBySortOrderAsc();
    }

    /** 기본 16문항으로 초기화 (기존 문항 모두 삭제). */
    @Transactional
    public List<ValueQuestion> resetToDefault() {
        repo.deleteAll();
        repo.flush();
        seedIfEmpty();
        return list();
    }
}
