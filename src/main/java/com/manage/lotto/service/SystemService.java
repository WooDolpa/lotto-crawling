package com.manage.lotto.service;

import com.manage.lotto.domain.LottoHistory;
import com.manage.lotto.dto.ExcelUploadResponse;
import com.manage.lotto.dto.SyncResponse;
import com.manage.lotto.event.LottoHistoryChanged;
import com.manage.lotto.exception.InvalidLottoDataException;
import com.manage.lotto.importer.DonghaengApiClient;
import com.manage.lotto.importer.LottoExcelParser;
import com.manage.lotto.importer.LottoExcelParser.DrawRow;
import com.manage.lotto.repository.LottoHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 동행복권 API·엑셀 파일로부터 당첨 이력 적재
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemService {

    /** 동행복권 서버 부하 방지 딜레이 */
    private static final long SYNC_DELAY_MS = 40;

    private final LottoHistoryRepository lottoHistoryRepository;
    private final DonghaengApiClient donghaengApiClient;
    private final LottoExcelParser excelParser;
    private final ApplicationEventPublisher events;

    /**
     * 저장된 최신 회차 다음부터 동행복권 API 데이터를 수집 및 저장
     *
     * @param limitSync 최대 동기화할 회차 개수 (0 이하일 경우 최신 회차까지 계속 수집)
     */
    @Transactional
    public SyncResponse syncFromDonghaengApi(int limitSync) {
        int startDrwNo = lottoHistoryRepository.findTopByOrderByDrwNoDesc()
                .map(h -> h.getDrwNo() + 1)
                .orElse(1);

        log.info("동행복권 API 동기화 시작 (시작 회차: {})", startDrwNo);

        int syncedCount = 0;
        int currentDrwNo = startDrwNo;

        while (limitSync <= 0 || syncedCount < limitSync) {
            try {
                Optional<LottoHistory> history = donghaengApiClient.fetchDraw(currentDrwNo);
                if (history.isEmpty()) {
                    break;
                }
                lottoHistoryRepository.save(history.get());
                syncedCount++;
                currentDrwNo++;
                Thread.sleep(SYNC_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("{}회차 동기화 중 오류 발생: {}", currentDrwNo, e.getMessage(), e);
                break;
            }
        }

        log.info("동행복권 API 동기화 완료 (총 {}건 적재)", syncedCount);
        if (syncedCount > 0) {
            events.publishEvent(new LottoHistoryChanged());
        }
        return new SyncResponse("SUCCESS", syncedCount, syncedCount + "개 회차 동기화가 완료되었습니다.");
    }

    /**
     * 엑셀 파일의 회차를 저장
     * 이미 있는 회차는 당첨 번호만 갱신하고, 엑셀의 빈 칸은 기존 값을 유지
     */
    @Transactional
    public ExcelUploadResponse saveExcel(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new InvalidLottoDataException("업로드된 엑셀 파일이 비어 있습니다.");
        }

        List<DrawRow> rows;
        try (InputStream input = file.getInputStream()) {
            rows = excelParser.parse(input);
        }

        Map<Integer, LottoHistory> existing = new HashMap<>();
        for (LottoHistory history : lottoHistoryRepository.findByDrwNoIn(rows.stream().map(DrawRow::drwNo).toList())) {
            existing.put(history.getDrwNo(), history);
        }

        List<LottoHistory> newHistories = new ArrayList<>();
        for (DrawRow row : rows) {
            LottoHistory current = existing.get(row.drwNo());
            if (current != null) {
                // 트랜잭션 커밋 시 변경 감지로 반영
                current.updateWinningInfo(row.numbers(), row.bonusNo(), row.firstWinAmt(), row.firstWinCo());
            } else {
                newHistories.add(LottoHistory.of(row.drwNo(), row.numbers(), row.bonusNo(), row.firstWinAmt(), row.firstWinCo()));
            }
        }

        lottoHistoryRepository.saveAll(newHistories);
        if (!rows.isEmpty()) {
            events.publishEvent(new LottoHistoryChanged());
        }

        int insertedCount = newHistories.size();
        int updatedCount = rows.size() - insertedCount;
        log.info("엑셀 파일 파싱 및 DB 적재 완료 (신규 {}건, 갱신 {}건)", insertedCount, updatedCount);

        Integer latestDrwNo = rows.stream().map(DrawRow::drwNo).max(Comparator.naturalOrder()).orElse(null);
        Integer oldestDrwNo = rows.stream().map(DrawRow::drwNo).min(Comparator.naturalOrder()).orElse(null);
        String message = String.format("신규 %d건, 갱신 %d건의 로또 당첨 데이터를 저장했습니다. (회차 범위: %s ~ %s)",
                insertedCount, updatedCount, drawLabel(oldestDrwNo), drawLabel(latestDrwNo));

        return new ExcelUploadResponse("SUCCESS", rows.size(), insertedCount, updatedCount, latestDrwNo, oldestDrwNo, message);
    }

    private static String drawLabel(Integer drwNo) {
        return drwNo != null ? drwNo + "회" : "-";
    }
}
