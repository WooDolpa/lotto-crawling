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
