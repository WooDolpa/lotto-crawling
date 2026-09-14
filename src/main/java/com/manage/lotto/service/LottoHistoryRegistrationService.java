package com.manage.lotto.service;

import com.manage.lotto.dto.*;
import com.manage.lotto.domain.LottoHistory;
import com.manage.lotto.repository.LottoHistoryRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Repository
@RequiredArgsConstructor
public class LottoHistoryRegistrationService {
    private final LottoHistoryRepository repository;
    private final EntityManager entityManager;
    private final ApplicationEventPublisher events;

    @Transactional
    public LottoHistoryResponse register(LottoHistoryCreateRequest request) {
        request.validate();
        if (repository.existsByDrwNo(request.drawNo())) throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 등록된 회차입니다.");
        var n = request.numbers().stream().sorted().toList();
        LottoHistory history = LottoHistory.builder().drwNo(request.drawNo()).drwDate(request.drawDate())
                .drwtNo1(n.get(0)).drwtNo2(n.get(1)).drwtNo3(n.get(2)).drwtNo4(n.get(3)).drwtNo5(n.get(4)).drwtNo6(n.get(5))
                .bnusNo(request.bonusNumber()).totSellamnt(request.totalSales() == null ? 0L : request.totalSales())
                .firstWinamnt(request.firstPrizeAmount() == null ? 0L : request.firstPrizeAmount())
                .firstPrzwnerCo(request.firstPrizeWinners() == null ? 0 : request.firstPrizeWinners()).build();
        // Insert only: concurrent registrations must never merge over an existing draw.
        entityManager.persist(history); entityManager.flush();
        events.publishEvent(new LottoHistoryChanged());
        return new LottoHistoryResponse(history.getDrwNo(), history.getDrwDate(), n);
    }
}
