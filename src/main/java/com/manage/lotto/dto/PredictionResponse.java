package com.manage.lotto.dto;

/**
 * 번호 예측 결과 (게임별 1게임)
 * <p>
 * 게임마다 뜻이 달라 목록이 아니라 이름 붙인 필드로 둔다. 순서가 아니라 이름으로 찾게 하려는 것이다.
 *
 * @param baseDrawNo  입력으로 사용한 마지막 회차
 * @param nextDrawNo  예측 대상 회차
 * @param pattern     A: 점수 모델 게임 (키 이름은 이전 패턴 모델에서 유래)
 * @param probability B: v1 확률 모델 게임
 * @param unpopular   C: 무작위로 뽑고 많이 고르는 조합을 제외한 게임
 * @param popularity  D: 인기도를 학습해 덜 붐비는 쪽으로 뽑은 게임
 * @param random      E: 아무 규칙 없이 뽑은 게임 (나머지 넷의 비교 기준)
 */
public record PredictionResponse(int baseDrawNo, int nextDrawNo, PredictedGame pattern, PredictedGame probability,
                                 PredictedGame unpopular, PredictedGame popularity, PredictedGame random) {}
