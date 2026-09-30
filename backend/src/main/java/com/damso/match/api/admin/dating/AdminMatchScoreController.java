package com.damso.match.api.admin.dating;

import com.damso.match.api.admin.dating.dto.MatchScoreMatrixDto;
import com.damso.match.domain.dating.service.MatchScoreService;
import com.damso.match.domain.event.entity.Customer;
import com.damso.match.domain.event.service.CustomerService;
import com.damso.match.global.common.Gender;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/match-scores")
@RequiredArgsConstructor
public class AdminMatchScoreController {

    private final CustomerService customerService;
    private final MatchScoreService matchScoreService;

    /**
     * 활성 남성 × 활성 여성 모든 쌍의 점수 매트릭스.
     *  - mbtiMatrix : MBTI 궁합 점수 (0~20)
     *  - valuesMatrix : 가치관 일치 점수 (0~80)
     *  - totalMatrix : 두 점수 합산 (0~100)
     */
    @GetMapping
    public MatchScoreMatrixDto matrix(@RequestParam Long eventId) {
        List<Customer> males = customerService.listActiveByGender(eventId, Gender.MALE);
        List<Customer> females = customerService.listActiveByGender(eventId, Gender.FEMALE);

        int n = males.size();
        int m = females.size();
        int[][] mbti = new int[n][m];
        int[][] values = new int[n][m];
        int[][] total = new int[n][m];

        for (int i = 0; i < n; i++) {
            Customer mc = males.get(i);
            for (int j = 0; j < m; j++) {
                Customer fc = females.get(j);
                int ms = matchScoreService.mbtiScore(mc.getMbti(), fc.getMbti());
                int vs = matchScoreService.valuesScore(mc.getValuesAnswers(), fc.getValuesAnswers());
                mbti[i][j] = ms;
                values[i][j] = vs;
                total[i][j] = ms + vs;
            }
        }

        return new MatchScoreMatrixDto(
                males.stream().map(c -> new MatchScoreMatrixDto.PersonBrief(c.getId(), c.getNickname(), c.getMbti())).toList(),
                females.stream().map(c -> new MatchScoreMatrixDto.PersonBrief(c.getId(), c.getNickname(), c.getMbti())).toList(),
                mbti, values, total
        );
    }
}