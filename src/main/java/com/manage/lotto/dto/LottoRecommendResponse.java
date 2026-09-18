package com.manage.lotto.dto;

import java.util.List;

/**
 * @param status     "SUCCESS" | "FAIL"
 * @param message    안내 메시지
 * @param baseDrawNo 학습 기준이 된 최신 회차 번호
 * @param games      추천된 5게임
 */
public record LottoRecommendResponse(String status, String message, Integer baseDrawNo,
                                     List<GameRecommendationDto> games) {}
