package com.manage.lotto.dto;

import java.util.List;

/**
 * 예측 모델 또는 규칙 1개가 만든 1게임
 *
 * @param available  예측 성공 여부
 * @param numbers    오름차순 번호 6개 (실패하면 빈 목록)
 * @param staleModel 학습 이후 이력이 바뀌어, 재학습 전 학습 결과로 계산했는지
 * @param message    예측하지 못한 이유 (성공하면 null)
 * @param model      예측에 사용한 모델 상태 (학습하지 않는 규칙 기반 게임은 null)
 */
public record PredictedGame(boolean available, List<Integer> numbers, boolean staleModel, String message,
                            ModelStatus model) {

    public static PredictedGame of(List<Integer> numbers, boolean staleModel, ModelStatus model) {
        return new PredictedGame(true, numbers, staleModel, null, model);
    }

    public static PredictedGame unavailable(String message, ModelStatus model) {
        return new PredictedGame(false, List.of(), false, message, model);
    }
}
