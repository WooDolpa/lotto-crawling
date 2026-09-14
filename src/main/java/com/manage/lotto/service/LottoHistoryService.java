package com.manage.lotto.service;

import com.manage.lotto.dto.LottoHistoryResponse;
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
        return repository.findByDrwNoBetweenOrderByDrwNoAsc(from, to).stream()
                .map(h -> new LottoHistoryResponse(h.getDrwNo(), h.getDrwDate(), h.getNumbers()))
                .toList();
    }
}
