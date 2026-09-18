package com.manage.lotto.service;

import com.manage.lotto.domain.LottoHistory;
import com.manage.lotto.domain.LottoRules;
import com.manage.lotto.domain.PrizeRank;
import com.manage.lotto.dto.SyncResponse;
import com.manage.lotto.event.LottoHistoryChanged;
import com.manage.lotto.exception.InvalidLottoDataException;
import com.manage.lotto.importer.DonghaengApiClient;
import com.manage.lotto.repository.LottoHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * 동행복권 API로부터 당첨 이력 적재
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemService {

    private final LottoHistoryRepository lottoHistoryRepository;
    private final DonghaengApiClient donghaengApiClient;
    private final ApplicationEventPublisher events;

    /**
     * 동행복권 API에서 회차 범위 당첨 정보를 받아 저장
     * <p>
     * 범위를 한 번에 받으므로 이미 있는 회차가 섞여 들어온다. 있는 회차는 갱신하고 없는 회차만 새로 넣는다.
     *
     * @param startNo 시작 회차
     * @param endNo   종료 회차 (시작 회차 이상이어야 한다)
     */
    @Transactional
    public SyncResponse syncData(int startNo, int endNo) {

        if (startNo <= 0) {
            throw new InvalidLottoDataException("시작 회차는 1 이상이어야 합니다.");
        }
        if (endNo < startNo) {
            throw new InvalidLottoDataException("종료 회차는 시작 회차 이상이어야 합니다. (시작 " + startNo + ", 종료 " + endNo + ")");
        }

        log.info("동행복권 API 동기화 시작 (시작 회차: {}, 종료 회차: {})", startNo, endNo);
        List<LottoHistory> fetched = new ArrayList<>();

        try {

            fetched = donghaengApiClient.fetchDraws(startNo, endNo);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("동행복권 API 동기화가 중단되었습니다.");
            return new SyncResponse("FAIL", 0, "동기화가 중단되었습니다.");
        } catch (IOException e) {
            log.error("동행복권 API 호출 실패: {}", e.getMessage(), e);
            return new SyncResponse("FAIL", 0, "동행복권 API를 호출하지 못했습니다. 잠시 후 다시 시도해 주세요.");
        }

        if (fetched.isEmpty()) {
            return new SyncResponse("SUCCESS", 0, "새로 받은 회차가 없습니다.");
        }

        Map<Integer, LottoHistory> existing = new HashMap<>();
        for (LottoHistory history : lottoHistoryRepository.findByDrwNoIn(fetched.stream().map(LottoHistory::getDrwNo).toList())) {
            existing.put(history.getDrwNo(), history);
        }

        List<LottoHistory> newHistories = new ArrayList<>();
        for (LottoHistory draw : fetched) {
            LottoHistory current = existing.get(draw.getDrwNo());
            if (current != null) {
                // 트랜잭션 커밋 시 변경 감지로 반영
                current.updateWinningInfo(draw.getNumbers(), draw.getBonusNo(), draw.getDrawDate(),
                        prizesOf(draw), draw.getTotalWinCo(), draw.getTotalSellAmt());
            } else {
                newHistories.add(draw);
            }
        }
        lottoHistoryRepository.saveAll(newHistories);
        events.publishEvent(new LottoHistoryChanged());

        int insertedCount = newHistories.size();
        int updatedCount = fetched.size() - insertedCount;
        log.info("동행복권 API 동기화 완료 (신규 {}건, 갱신 {}건)", insertedCount, updatedCount);
        String message = String.format("신규 %d건, 갱신 %d건의 회차를 동기화했습니다. (회차 범위: %s ~ %s)",
                insertedCount, updatedCount, drawLabel(fetched.get(0).getDrwNo()),
                drawLabel(fetched.get(fetched.size() - 1).getDrwNo()));
        return new SyncResponse("SUCCESS", fetched.size(), message);
    }

    /**
     * 조회한 회차의 1~5등 당첨 정보를 등수 순서대로 모음
     */
    private static List<PrizeRank> prizesOf(LottoHistory draw) {
        return IntStream.rangeClosed(1, LottoRules.PRIZE_RANKS).mapToObj(draw::getPrize).toList();
    }

    private static String drawLabel(Integer drwNo) {
        return drwNo != null ? drwNo + "회" : "-";
    }
}
