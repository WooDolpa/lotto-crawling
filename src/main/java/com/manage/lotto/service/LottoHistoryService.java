package com.manage.lotto.service;

import com.manage.lotto.dto.LottoHistoryResponse;
import com.manage.lotto.exception.InvalidLottoDataException;
import com.manage.lotto.repository.LottoHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LottoHistoryService {

    private final LottoHistoryRepository repository;

    public List<Integer> draws() {
        return repository.findDrawNumbers();
    }

    public List<LottoHistoryResponse> history(int from, int to) {
        if (from <= 0 || to <= 0 || from > to) {
            throw new InvalidLottoDataException("올바른 추첨 회차 범위를 선택해 주세요.");
        }
        return repository.findByDrwNoBetweenOrderByDrwNoAsc(from, to).stream()
                .map(LottoHistoryResponse::from)
                .toList();
    }
}
