package com.manage.lotto.dto;

import java.time.Instant;

/**
 * 시간순 검증 작업 상태 (서버 메모리에 마지막 1건만 보관)
 *
 * @param state          IDLE(실행 이력 없음), RUNNING, DONE, FAILED
 * @param testDraws      요청한 검증 회차 수
 * @param completedSteps 끝난 단계 수 (특징 계산 1단계 + 학습 구간 수)
 * @param totalSteps     전체 단계 수
 * @param startedAt      시작 시각
 * @param finishedAt     종료 시각
 * @param error          실패 사유
 * @param report         완료 결과 (DONE일 때만)
 * @param maxTestDraws   현재 저장된 이력으로 실행할 수 있는 최대 검증 회차 수 (최소 회차 수보다 적으면 0)
 */
public record ValidationStatus(State state, Integer testDraws, int completedSteps, int totalSteps, Instant startedAt,
                               Instant finishedAt, String error, ValidationReport report, int maxTestDraws) {

    public enum State { IDLE, RUNNING, DONE, FAILED }

    public static ValidationStatus idle() {
        return new ValidationStatus(State.IDLE, null, 0, 0, null, null, null, null, 0);
    }

    public static ValidationStatus running(int testDraws, int totalSteps) {
        return new ValidationStatus(State.RUNNING, testDraws, 0, totalSteps, Instant.now(), null, null, null, 0);
    }

    public ValidationStatus progress(int completed) {
        return new ValidationStatus(state, testDraws, completed, totalSteps, startedAt, null, null, null, maxTestDraws);
    }

    public ValidationStatus done(ValidationReport result) {
        return new ValidationStatus(State.DONE, testDraws, totalSteps, totalSteps, startedAt, Instant.now(), null, result, maxTestDraws);
    }

    public ValidationStatus failed(String message) {
        return new ValidationStatus(State.FAILED, testDraws, completedSteps, totalSteps, startedAt, Instant.now(), message, null, maxTestDraws);
    }

    public ValidationStatus withMaxTestDraws(int max) {
        return new ValidationStatus(state, testDraws, completedSteps, totalSteps, startedAt, finishedAt, error, report, max);
    }
}
