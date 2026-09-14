package com.manage.lotto.controller.api;

import com.manage.lotto.dto.LottoRecommendResponse;
import com.manage.lotto.service.LottoRecommendationService;
import com.manage.lotto.service.LottoHistoryService;
import com.manage.lotto.dto.LottoHistoryResponse;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.List;
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
    private final LottoHistoryService lottoHistoryService;

    @GetMapping("/draws")
    public List<Integer> draws() {
        return lottoHistoryService.draws();
    }

    @GetMapping("/history")
    public List<LottoHistoryResponse> history(
            @RequestParam(name = "fromDrawNo") int fromDrawNo,
            @RequestParam(name = "toDrawNo") int toDrawNo) {
        if (fromDrawNo <= 0 || toDrawNo <= 0 || fromDrawNo > toDrawNo) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "올바른 추첨 회차 범위를 선택해 주세요.");
        }
        return lottoHistoryService.history(fromDrawNo, toDrawNo);
    }

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
