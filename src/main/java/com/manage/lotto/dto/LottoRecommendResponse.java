package com.manage.lotto.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LottoRecommendResponse {
    private String status;                      // "SUCCESS" | "FAIL"
    private String message;                     // 안내 메시지
    private Integer baseDrawNo;                 // 학습 및 기준이 된 최신 회차 번호
    private List<GameRecommendationDto> games; // 추천된 5게임
}
