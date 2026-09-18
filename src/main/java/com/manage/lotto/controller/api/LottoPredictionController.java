package com.manage.lotto.controller.api;

import com.manage.lotto.dto.PredictionResponse;
import com.manage.lotto.dto.PredictionStatusResponse;
import com.manage.lotto.service.LottoPredictionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 번호 예측 API (A 점수 모델 + B v1 확률 모델 + C 인기 조합 제외 규칙 + D 인기도 모델 + E 무작위 기준선) 상태·학습·예측
 */
@RestController
@RequestMapping("/api/lotto/prediction")
@RequiredArgsConstructor
public class LottoPredictionController {

    private final LottoPredictionService predictionService;

    /**
     * 모델 상태 조회
     * GET /api/lotto/prediction/status
     * <p>
     * 학습 작업 진행 여부와 모델별 사용 가능 여부, 학습 기준 회차, 학습 건수·샘플 수, 학습 시각, 최근 오류를 반환한다.
     * 번호 예측 화면에서 사용한다.
     */
    @GetMapping("/status")
    public PredictionStatusResponse status() {
        return predictionService.status();
    }

    /**
     * 모델 재학습 요청
     * POST /api/lotto/prediction/train
     * <p>
     * 학습 모델(A·B·D)의 변경 확인을 백그라운드로 요청하고 즉시 202와 현재 상태를 반환한다.
     * 당첨 이력이 마지막 학습 때와 같은 모델은 다시 학습하지 않는다.
     */
    @PostMapping("/train")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public PredictionStatusResponse train() {
        return predictionService.requestTraining();
    }

    /**
     * 다음 회차 번호 예측
     * GET /api/lotto/prediction
     * <p>
     * 최신 회차 다음 회차의 번호를 5게임(A·B·C·D·E) 만든다. 예측 요청은 학습하지 않는다.
     * <ul>
     *     <li>A 점수 모델: 번호별 출현 점수 상위 6개 (같은 모델·이력이면 항상 같은 번호)</li>
     *     <li>B 확률 모델: 번호별 출현 확률 가중 샘플링 + 밸런스 필터 (호출마다 달라질 수 있음)</li>
     *     <li>C 인기 조합 제외: 무작위로 뽑고 6개가 모두 31 이하인 생일 조합만 제외 (모델 없음, model=null)</li>
     *     <li>D 인기도 모델: 조합의 생김새로 인기도를 학습해, 후보 중 덜 붐비는 조합 선택
     *     (popularity에 평균 대비 예상 인기도를 담는다)</li>
     *     <li>E 무작위 기준선: 규칙 없이 1~45에서 6개 (모델 없음, model=null). 나머지 넷의 비교 기준</li>
     * </ul>
     * A·B·D 모델이 예측하지 못하면 그 게임은 available=false와 사유를 담는다.
     * 학습 이후 이력이 바뀌었으면 게임별 staleModel=true로 표시한다.
     * 이력이 없으면 400.
     */
    @GetMapping
    public PredictionResponse predict() {
        return predictionService.predict();
    }
}
