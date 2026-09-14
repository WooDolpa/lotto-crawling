package com.manage.lotto.dto;

import java.time.LocalDate;
import java.util.List;

public record LottoHistoryResponse(Integer drawNo, LocalDate drawDate, List<Integer> numbers) {}
