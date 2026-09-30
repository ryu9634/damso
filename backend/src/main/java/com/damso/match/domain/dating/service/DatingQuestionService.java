package com.damso.match.domain.dating.service;

import com.damso.match.domain.dating.entity.DatingAnswer;
import com.damso.match.domain.dating.entity.DatingProfile;
import com.damso.match.domain.dating.entity.DatingQuestion;
import com.damso.match.domain.dating.repository.DatingAnswerRepository;
import com.damso.match.domain.dating.repository.DatingProfileRepository;
import com.damso.match.domain.dating.repository.DatingQuestionRepository;
import com.damso.match.domain.setting.entity.AppSetting;
import com.damso.match.domain.setting.repository.AppSettingRepository;
import com.damso.match.domain.dating.entity.DatingQuestion.QType;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * 소개팅 상세 질문(커스텀) 관리.
 *  - 관리자 CRUD + 정렬.
 *  - 최초 실행 시 기존 고정 항목(키/몸무게/MBTI/성격/취미/장점/단점)을 기본 질문으로 시드하고,
 *    이미 등록된 프로필의 상세 데이터를 답변으로 이전한다.
 *  - 나중에 추가된 기본 질문(직업·주량·좋아하는 술 등)은 이미 운영 중인 곳에도 한 번만 채워 넣는다.
 *    관리자가 지운 질문을 되살리지 않도록, 추가 여부는 설정값 하나로 기억한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DatingQuestionService {

    private final DatingQuestionRepository repo;
    private final DatingAnswerRepository answerRepo;
    private final DatingProfileRepository profileRepo;
    private final AppSettingRepository settingRepo;

    /** 나중에 추가한 기본 질문을 이미 채웠는지 기억하는 설정 키. */
    private static final String EXTRA_SEEDED_KEY = "dating.questions.extraSeeded";

    private static final String MBTI_OPTIONS = String.join("\n",
            "INTJ", "INTP", "ENTJ", "ENTP", "INFJ", "INFP", "ENFJ", "ENFP",
            "ISTJ", "ISFJ", "ESTJ", "ESFJ", "ISTP", "ISFP", "ESTP", "ESFP");

    /** 기본 질문 정의: label, type, options, required, 레거시 프로필 값 추출기. */
    private record Seed(String label, QType type, String options, boolean required,
                        Function<DatingProfile, String> legacy) {}

    private static final List<Seed> DEFAULTS = List.of(
            new Seed("키 (cm)", QType.NUMBER, null, true, p -> str(p.getHeight())),
            new Seed("몸무게 (kg)", QType.NUMBER, null, true, p -> str(p.getWeight())),
            new Seed("MBTI", QType.SELECT, MBTI_OPTIONS, true, DatingProfile::getMbti),
            new Seed("성격", QType.TEXTAREA, null, true, DatingProfile::getPersonality),
            new Seed("취미 및 특기", QType.TEXTAREA, null, true, DatingProfile::getHobbies),
            new Seed("장점", QType.TEXTAREA, null, true, DatingProfile::getStrengths),
            new Seed("단점", QType.TEXTAREA, null, true, DatingProfile::getWeaknesses)
    );

    /** 술집이라서 물어볼 수 있는 것들 — 나중에 추가된 기본 질문. 모두 선택 입력이다. */
    private static final List<Seed> EXTRAS = List.of(
            new Seed("직업 / 하는 일", QType.TEXT, null, false, p -> null),
            new Seed("사는 곳 (동네)", QType.TEXT, null, false, p -> null),
            new Seed("좋아하는 술", QType.SELECT, String.join("\n",
                    "맥주", "소주", "하이볼", "위스키", "와인", "막걸리 · 전통주", "칵테일",
                    "사케", "논알콜 · 잘 못 마셔요"), false, p -> null),
            new Seed("주량", QType.SELECT, String.join("\n",
                    "거의 못 마셔요", "가볍게 한두 잔", "소주 1병 정도", "소주 2병 이상",
                    "술보다 분위기를 좋아해요"), false, p -> null),
            new Seed("흡연", QType.SELECT, String.join("\n",
                    "비흡연", "가끔 피워요", "흡연", "전자담배"), false, p -> null),
            new Seed("주로 만날 수 있는 때", QType.SELECT, String.join("\n",
                    "평일 저녁", "주말", "평일 · 주말 다 괜찮아요"), false, p -> null),
            new Seed("이런 분이면 좋겠어요", QType.TEXTAREA, null, false, p -> null),
            new Seed("첫 만남에서 하고 싶은 것", QType.TEXTAREA, null, false, p -> null)
    );

    // ─── 시드 + 이전 ────────────────────────────────────
    @PostConstruct
    public void init() {
        List<DatingQuestion> questions = repo.findAllByOrderBySortOrderAsc();
        if (questions.isEmpty()) {
            questions = seedDefaults();
        }
        migrateLegacy(questions);
        seedExtrasOnce();
    }

    /**
     * 나중에 추가된 기본 질문을 한 번만 채운다.
     * 관리자가 필요 없다고 지운 질문이 앱을 켤 때마다 되살아나면 안 되므로,
     * '이미 채웠다'를 설정값으로 남기고 그 뒤로는 손대지 않는다.
     */
    @Transactional
    public void seedExtrasOnce() {
        if (settingRepo.findById(EXTRA_SEEDED_KEY).isPresent()) return;

        List<String> labels = repo.findAll().stream().map(DatingQuestion::getLabel).toList();
        int order = repo.findAll().stream().mapToInt(DatingQuestion::getSortOrder).max().orElse(-1) + 1;
        int added = 0;
        for (Seed s : EXTRAS) {
            if (labels.contains(s.label())) continue;
            repo.save(DatingQuestion.builder()
                    .sortOrder(order++)
                    .label(s.label())
                    .type(s.type())
                    .options(s.options())
                    .required(s.required())
                    .build());
            added++;
        }
        settingRepo.save(new AppSetting(EXTRA_SEEDED_KEY, String.valueOf(added)));
    }

    /** 기본 7개 질문을 시드하고 생성 목록을 돌려준다. */
    private List<DatingQuestion> seedDefaults() {
        List<DatingQuestion> created = new ArrayList<>();
        int order = 0;
        for (Seed s : DEFAULTS) {
            created.add(repo.save(DatingQuestion.builder()
                    .sortOrder(order++)
                    .label(s.label())
                    .type(s.type())
                    .options(s.options())
                    .required(s.required())
                    .build()));
        }
        return created;
    }

    /**
     * 기존 프로필의 레거시 상세 컬럼을 답변으로 이전한다.
     *  - 프로필별로 답변이 하나도 없을 때만 채워 idempotent 하게 동작.
     *  - 기본 질문 라벨과 매칭해 값을 옮긴다.
     */
    private void migrateLegacy(List<DatingQuestion> questions) {
        for (DatingProfile p : profileRepo.findAll()) {
            if (!answerRepo.findByProfileId(p.getId()).isEmpty()) continue;
            for (Seed s : DEFAULTS) {
                String v = s.legacy().apply(p);
                if (v == null || v.isBlank()) continue;
                Long qid = questions.stream()
                        .filter(q -> q.getLabel().equals(s.label()))
                        .map(DatingQuestion::getId).findFirst().orElse(null);
                if (qid == null) continue;
                answerRepo.save(DatingAnswer.builder()
                        .profileId(p.getId()).questionId(qid).value(v.trim()).build());
            }
        }
    }

    // ─── 조회 ───────────────────────────────────────────
    public List<DatingQuestion> list() {
        return repo.findAllByOrderBySortOrderAsc();
    }

    // ─── CRUD ───────────────────────────────────────────
    @Transactional
    public DatingQuestion create(String label, QType type, String options, boolean required) {
        int next = repo.findAll().stream().mapToInt(DatingQuestion::getSortOrder).max().orElse(-1) + 1;
        return repo.save(DatingQuestion.builder()
                .sortOrder(next)
                .label(require(label, "질문 제목"))
                .type(type == null ? QType.TEXT : type)
                .options(normalizeOptions(type, options))
                .required(required)
                .build());
    }

    @Transactional
    public DatingQuestion update(Long id, String label, QType type, String options, boolean required) {
        DatingQuestion q = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("질문을 찾을 수 없습니다."));
        q.setLabel(require(label, "질문 제목"));
        q.setType(type == null ? QType.TEXT : type);
        q.setOptions(normalizeOptions(type, options));
        q.setRequired(required);
        return repo.save(q);
    }

    @Transactional
    public void delete(Long id) {
        if (!repo.existsById(id)) throw new IllegalArgumentException("질문을 찾을 수 없습니다.");
        answerRepo.deleteByQuestionId(id);
        repo.deleteById(id);
    }

    @Transactional
    public List<DatingQuestion> move(Long id, boolean up) {
        List<DatingQuestion> all = repo.findAllByOrderBySortOrderAsc();
        int idx = -1;
        for (int i = 0; i < all.size(); i++) if (all.get(i).getId().equals(id)) idx = i;
        if (idx < 0) throw new IllegalArgumentException("질문을 찾을 수 없습니다.");
        int swap = up ? idx - 1 : idx + 1;
        if (swap < 0 || swap >= all.size()) return all;
        DatingQuestion a = all.get(idx), b = all.get(swap);
        int tmp = a.getSortOrder();
        a.setSortOrder(b.getSortOrder());
        b.setSortOrder(tmp);
        repo.save(a);
        repo.save(b);
        return repo.findAllByOrderBySortOrderAsc();
    }

    // ─── 헬퍼 ───────────────────────────────────────────
    private static String normalizeOptions(QType type, String options) {
        if (type != QType.SELECT) return null;
        if (options == null || options.isBlank()) return null;
        // 줄 단위 정리(공백/빈 줄 제거)
        return options.lines()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .reduce((a, b) -> a + "\n" + b)
                .orElse(null);
    }

    private static String require(String s, String field) {
        if (s == null || s.isBlank()) throw new IllegalArgumentException(field + "을(를) 입력하세요.");
        return s.trim();
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}