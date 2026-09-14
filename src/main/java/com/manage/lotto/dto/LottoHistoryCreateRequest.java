package com.manage.lotto.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.HashSet;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public record LottoHistoryCreateRequest(Integer drawNo, LocalDate drawDate, List<Integer> numbers,
        Integer bonusNumber, Long totalSales, Long firstPrizeAmount, Integer firstPrizeWinners) {
    public void validate() {
        if (drawNo == null || drawNo <= 0 || drawDate == null || numbers == null || numbers.size() != 6 ||
                numbers.stream().anyMatch(n -> n == null || n < 1 || n > 45) || new HashSet<>(numbers).size() != 6 ||
                bonusNumber == null || bonusNumber < 1 || bonusNumber > 45 || numbers.contains(bonusNumber) ||
                (totalSales != null && totalSales < 0) || (firstPrizeAmount != null && firstPrizeAmount < 0) ||
                (firstPrizeWinners != null && firstPrizeWinners < 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "필수 정보를 입력하고 번호 범위·중복 및 금액을 확인해 주세요.");
        }
    }
}
