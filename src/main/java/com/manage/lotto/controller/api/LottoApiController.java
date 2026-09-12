package com.manage.lotto.controller.api;

import com.manage.lotto.dto.LottoRecommendResponse;
import com.manage.lotto.service.LottoRecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/lotto")
@RequiredArgsConstructor
public class LottoApiController {

    private final LottoRecommendationService lottoRecommendationService;

    /**
     * AI 기반 로또 5게임 번호 추천 API
     * GET /api/lotto/recommend
     */
    @GetMapping("/recommend")
    public ResponseEntity<LottoRecommendResponse> recommend() {
        LottoRecommendResponse response = lottoRecommendationService.recommend5Games();
        return ResponseEntity.ok(response);
    }
}
