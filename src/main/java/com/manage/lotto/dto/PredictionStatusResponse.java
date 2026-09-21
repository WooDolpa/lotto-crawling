package com.manage.lotto.dto;

/**
 * 번호 예측 모델 상태
 *
 * @param training    변경 확인 또는 학습 작업이 진행 중인지
 * @param pattern     A 점수 모델 상태 (키 이름은 이전 패턴 모델에서 유래)
 * @param probability B v1 확률 모델 상태
 * @param popularity  E 인기도 모델 상태
 */
public record PredictionStatusResponse(boolean training, ModelStatus pattern, ModelStatus probability,
                                       ModelStatus popularity) {}
