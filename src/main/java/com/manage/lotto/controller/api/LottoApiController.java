package com.manage.lotto.controller.api;

import com.manage.lotto.dto.FirstPrizeWinnersResponse;
import com.manage.lotto.dto.LottoHistoryResponse;
import com.manage.lotto.dto.LottoRecommendResponse;
import com.manage.lotto.service.LottoHistoryService;
import com.manage.lotto.service.LottoRecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 당첨 이력 조회·통계 및 v1 번호 추천 API
 */
@RestController
@RequestMapping("/api/lotto")
@RequiredArgsConstructor
public class LottoApiController {

    private final LottoRecommendationService lottoRecommendationService;
    private final LottoHistoryService lottoHistoryService;

    /**
     * 저장된 회차 번호 목록 조회
     * GET /api/lotto/draws
     * <p>
     * DB에 저장된 전체 회차 번호를 오름차순으로 반환한다.
     * 당첨 이력 화면(/)의 시작·종료 회차 선택 목록에 사용한다.
     */
    @GetMapping("/draws")
    public List<Integer> draws() {
        return lottoHistoryService.draws();
    }

    /**
     * 회차 범위 당첨 이력 조회
     * GET /api/lotto/history?fromDrawNo=1&toDrawNo=10
     * <p>
     * 범위 안의 회차별 당첨 번호 6개(오름차순)를 회차순으로 반환한다.
     * 당첨 이력 화면(/)의 번호 목록·용지 카드에 사용한다.
     *
     * @param fromDrawNo 시작 회차 (1 이상)
     * @param toDrawNo   종료 회차 (시작 회차 이상, 범위가 잘못되면 400)
     */
    @GetMapping("/history")
    public List<LottoHistoryResponse> history(@RequestParam(name = "fromDrawNo") int fromDrawNo,
                                              @RequestParam(name = "toDrawNo") int toDrawNo) {
        return lottoHistoryService.history(fromDrawNo, toDrawNo);
    }

    /**
     * 회차별 1등 당첨자 수 통계
     * GET /api/lotto/statistics/first-prize-winners
     * <p>
     * 전체 회차의 추첨일·1등 당첨자 수·1게임당 1등 당첨금·판매량으로 본 기대 1등 당첨자 수를 회차순으로 반환한다.
     * 동기화 전 회차는 그 값들이 null이고, 기대 당첨자 수는 836회 미만도 null이다.
     * 통계 > 1등 당첨자수 화면의 기간별 요약·차트·연도별 비교에 사용한다.
     */
    @GetMapping("/statistics/first-prize-winners")
    public List<FirstPrizeWinnersResponse> firstPrizeWinners() {
        return lottoHistoryService.firstPrizeWinners();
    }

    /**
     * v1 머신러닝 기반 5게임 번호 추천
     * GET /api/lotto/recommend
     * <p>
     * 미리 학습해 둔 번호별 출현 확률로 확률 가중 샘플링과 밸런스 필터
     * (총합 100~175, 홀짝·고저 2~4개, 3연번 제외)를 적용해 중복 없는 5게임을 만든다.
     * 게임마다 총합·홀짝·고저 비율·연번 여부를 함께 반환한다.
     * 이 요청은 학습하지 않으며(학습 시점은 LottoModelTrainingService), 아직 학습 전이면 503.
     * 이력이 25회 미만이면 균등 확률을 사용한다. 현재 이 API를 호출하는 화면은 없다.
     */
    @GetMapping("/recommend")
    public LottoRecommendResponse recommend() {
        return lottoRecommendationService.recommend5Games();
    }
}
