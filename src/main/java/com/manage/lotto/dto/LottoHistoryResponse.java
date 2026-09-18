package com.manage.lotto.dto;

import com.manage.lotto.domain.LottoHistory;

import java.util.List;

public record LottoHistoryResponse(Integer drawNo, List<Integer> numbers) {

    public static LottoHistoryResponse from(LottoHistory history) {
        return new LottoHistoryResponse(history.getDrwNo(), history.getNumbers());
    }
}
