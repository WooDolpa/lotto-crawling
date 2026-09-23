package com.manage.lotto.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * packageName : com.manage.lotto.controller
 * className : HomeController
 * user : jwlee
 * date : 2026. 1. 19.
 * description : 화면 라우트 (Thymeleaf 뷰 이름 반환)
 */
@Controller
public class HomeController {

    /**
     * 당첨 이력 화면
     * GET /
     * <p>
     * 회차 범위를 골라 당첨 번호를 번호 목록 또는 용지 카드로 조회한다.
     */
    @GetMapping(path = "/")
    public String index() {
        return "index";
    }

    /**
     * 번호 예측 화면 단축 경로
     * GET /prediction → /lotto/prediction 으로 리다이렉트
     */
    @GetMapping("/prediction")
    public String prediction() {
        return "redirect:/lotto/prediction";
    }

    /**
     * 번호 예측 화면
     * GET /lotto/prediction
     * <p>
     * A 점수 모델·B v1 확률 모델·C·D 동반출현 게임·E 인기도 모델로 다음 회차 번호를 1게임씩 예측하고, 모델 학습 상태를 확인·갱신한다.
     */
    @GetMapping("/lotto/prediction")
    public String lottoPrediction() {
        return "prediction";
    }

    /**
     * 모델 검증 화면
     * GET /validation
     * <p>
     * 최근 회차로 시간순 검증을 백그라운드에서 실행해 A·B·C·D 게임의 일치 개수를 무작위 이론값과 통계로 비교한다.
     */
    @GetMapping("/validation")
    public String validation() {
        return "validation";
    }

    /**
     * 통계 메뉴 단축 경로
     * GET /statistics → 첫 하위 화면(/statistics/first-prize-winners)으로 리다이렉트
     */
    @GetMapping("/statistics")
    public String statistics() {
        return "redirect:/statistics/first-prize-winners";
    }

    /**
     * 통계 > 1등 당첨자수 화면
     * GET /statistics/first-prize-winners
     */
    @GetMapping("/statistics/first-prize-winners")
    public String firstPrizeWinners() {
        return "first-prize-winners";
    }
}
