package com.manage.lotto.dto;

import com.manage.lotto.domain.LottoRules;
import com.manage.lotto.exception.InvalidLottoDataException;

import java.util.List;

public record LottoHistoryCreateRequest(Integer drawNo, List<Integer> numbers, Integer bonusNumber,
                                        Long firstPrizeAmount, Integer firstPrizeWinners) {

    public void validate() {
        boolean invalid = drawNo == null || drawNo <= 0
                || !LottoRules.isValidNumbers(numbers)
                || !LottoRules.isValidNumber(bonusNumber) || numbers.contains(bonusNumber)
                || isNegative(firstPrizeAmount) || isNegative(firstPrizeWinners);
        if (invalid) {
            throw new InvalidLottoDataException("필수 정보를 입력하고 번호 범위·중복 및 금액을 확인해 주세요.");
        }
    }

    private static boolean isNegative(Number value) {
        return value != null && value.longValue() < 0;
    }
}
