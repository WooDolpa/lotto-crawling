package com.manage.lotto.dto;

/**
 * 번호 예측 결과 (게임별 1게임)
 *
 * @param baseDrawNo  입력으로 사용한 마지막 회차
 * @param nextDrawNo  예측 대상 회차
 * @param pattern     A: 점수 모델 게임 (키 이름은 이전 패턴 모델에서 유래)
 * @param probability B: v1 확률 모델 게임
 * @param unpopular   C: 무작위로 뽑고 많이 고르는 조합을 제외한 게임
 */
public record PredictionResponse(int baseDrawNo, int nextDrawNo, PredictedGame pattern, PredictedGame probability,
                                 PredictedGame unpopular) {}
