package com.manage.lotto.dto;

import java.time.Instant;

/**
 * 예측 모델 1개의 학습 상태
 *
 * @param available         예측에 쓸 학습 결과가 있는지
 * @param trainedBaseDrawNo 학습에 사용한 마지막 회차 (이력이 없으면 null)
 * @param historyCount      학습에 사용한 이력 건수
 * @param trainingSamples   학습 샘플 수 (21번째 회차부터 회차 × 45개 번호)
 * @param trainedAt         학습 완료 시각
 * @param lastError         최근 학습 오류 (없으면 null)
 */
public record ModelStatus(boolean available, Integer trainedBaseDrawNo, Integer historyCount,
                          Integer trainingSamples, Instant trainedAt, String lastError) {

    public static ModelStatus unavailable(String lastError) {
        return new ModelStatus(false, null, null, null, null, lastError);
    }
}
