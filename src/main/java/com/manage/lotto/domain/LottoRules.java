package com.manage.lotto.domain;

import com.manage.lotto.exception.InvalidLottoDataException;

import java.util.HashSet;
import java.util.List;

/**
 * 로또 6/45 번호 규칙과 당첨 이력 검증
 */
public final class LottoRules {

    public static final int MIN_NUMBER = 1;
    public static final int MAX_NUMBER = 45;
    public static final int NUMBERS_PER_DRAW = 6;
    /** 당첨 등수 (1~5등) */
    public static final int PRIZE_RANKS = 5;
    /** 로또 용지 한 줄의 칸 수 (lotto-sheet.js 용지 배치와 같음) */
    public static final int SHEET_COLUMNS = 7;
    /** 6/45 전체 조합 수 = C(45,6) */
    public static final int TOTAL_COMBINATIONS = 8_145_060;
    /** 1게임 가격 (원). 총 판매금액을 판매 게임 수로 바꾸는 데 쓴다. */
    public static final int GAME_PRICE = 1_000;
    /**
     * 인기도 계산을 신뢰할 수 있는 첫 회차 (2018-12-08, 동행복권 수탁 시작)
     * <p>
     * 이전 회차는 위탁 운영 기관이 달라 판매금액·당첨자 수 집계와 구매 환경이 지금과 다르다.
     * 인기도를 같은 자로 잴 수 없으므로 인기도 모델(E)의 학습·검증에서 제외한다.
     * 추첨된 공 자체는 운영 기관과 무관하므로 번호만 쓰는 계산(A·B 특징, C·D 동반출현, 적중률 검증)에는 적용하지 않는다.
     */
    public static final int FIRST_TRUSTED_DRAW = 836;

    private LottoRules() {
    }

    private static boolean isValidNumber(Integer number) {
        return number != null && number >= MIN_NUMBER && number <= MAX_NUMBER;
    }

    /**
     * 1~45 범위의 서로 다른 번호 6개인지 확인
     */
    public static boolean isValidNumbers(List<Integer> numbers) {
        return numbers != null
                && numbers.size() == NUMBERS_PER_DRAW
                && numbers.stream().allMatch(LottoRules::isValidNumber)
                && new HashSet<>(numbers).size() == NUMBERS_PER_DRAW;
    }

    /**
     * 회차 오름차순 이력이 빠짐없이 이어지고 모든 당첨 번호가 유효한지 검증
     */
    public static void requireContinuousHistory(List<LottoHistory> histories) {
        for (int i = 0; i < histories.size(); i++) {
            LottoHistory history = histories.get(i);
            boolean continuous = i == 0 || history.getDrwNo() == histories.get(i - 1).getDrwNo() + 1;
            if (history.getDrwNo() <= 0 || !continuous || !isValidNumbers(history.getNumbers())) {
                throw new InvalidLottoDataException("회차 누락 또는 유효하지 않은 당첨 번호가 있습니다.");
            }
        }
    }
}
