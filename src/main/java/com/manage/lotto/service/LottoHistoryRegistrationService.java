package com.manage.lotto.service;

import com.manage.lotto.domain.LottoHistory;
import com.manage.lotto.dto.LottoHistoryCreateRequest;
import com.manage.lotto.dto.LottoHistoryResponse;
import com.manage.lotto.event.LottoHistoryChanged;
import com.manage.lotto.exception.DuplicateDrawException;
import com.manage.lotto.repository.LottoHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LottoHistoryRegistrationService {

    private final LottoHistoryRepository repository;
    private final ApplicationEventPublisher events;

    @Transactional
    public LottoHistoryResponse register(LottoHistoryCreateRequest request) {
        request.validate();
        if (repository.existsByDrwNo(request.drawNo())) {
            throw new DuplicateDrawException("이미 등록된 회차입니다.");
        }
        List<Integer> numbers = request.numbers().stream().sorted().toList();
        LottoHistory history = LottoHistory.of(request.drawNo(), numbers, request.bonusNumber(),
                request.firstPrizeAmount(), request.firstPrizeWinners());
        // 동시 등록으로 같은 회차가 들어오면 drw_no 유니크 인덱스 위반(DataIntegrityViolationException, 409)
        repository.saveAndFlush(history);
        events.publishEvent(new LottoHistoryChanged());
        return LottoHistoryResponse.from(history);
    }
}
