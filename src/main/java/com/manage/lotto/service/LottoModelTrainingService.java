package com.manage.lotto.service;

import com.manage.lotto.domain.LottoHistory;
import com.manage.lotto.event.LottoHistoryChanged;
import com.manage.lotto.repository.LottoHistoryRepository;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 예측 모델(A 점수 모델, B v1 확률 모델)의 학습 시점 관리
 * <p>
 * 시작 시, 이력 변경 커밋 후, 주기 점검(기본 1시간) 때 학습을 요청한다.
 * 한 백그라운드 스레드에서 두 모델을 차례로 확인하며, 각 모델은 이력이 마지막 학습 때와 다를 때만 다시 학습한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LottoModelTrainingService {

    private final LottoHistoryRepository repository;
    private final LottoPatternModelService patternModel;
    private final LottoRecommendationService probabilityModel;

    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "model-training");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicBoolean dirty = new AtomicBoolean();
    private volatile boolean closed;

    @EventListener(ApplicationReadyEvent.class)
    public void startup() {
        requestRefresh();
    }

    @TransactionalEventListener
    public void onHistoryChanged(LottoHistoryChanged event) {
        requestRefresh();
    }

    // Also detects committed changes made directly in DB; unchanged data does not train.
    @Scheduled(fixedDelayString = "${lotto.model.check-interval-ms:3600000}", initialDelayString = "${lotto.model.check-interval-ms:3600000}")
    public void checkForChanges() {
        requestRefresh();
    }

    /**
     * 학습 요청 (실행 중이면 dirty 표시만 하고 현재 작업 종료 후 다시 확인)
     */
    public void requestRefresh() {
        if (closed) {
            return;
        }
        dirty.set(true);
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            worker.execute(this::refreshWhileDirty);
        } catch (RejectedExecutionException e) {
            running.set(false);
            if (!closed) {
                throw e;
            }
        }
    }

    /**
     * 변경 확인 또는 학습 작업이 진행 중인지
     */
    public boolean isTraining() {
        return running.get();
    }

    private void refreshWhileDirty() {
        try {
            do {
                dirty.set(false);
                refresh();
            } while (dirty.get() && !closed);
        } finally {
            running.set(false);
            if (dirty.get() && !closed) {
                requestRefresh();
            }
        }
    }

    private void refresh() {
        List<LottoHistory> histories;
        try {
            histories = repository.findAllByOrderByDrwNoAsc();
        } catch (Exception e) {
            log.error("학습용 당첨 이력을 불러오지 못했습니다. 다음 요청 때 다시 확인합니다.", e);
            return;
        }
        // 각 모델은 실패해도 오류만 기록하므로 한 모델의 실패가 다른 모델 학습을 막지 않는다.
        patternModel.refresh(histories);
        probabilityModel.refresh(histories);
    }

    @PreDestroy
    public void close() {
        closed = true;
        worker.shutdownNow();
    }
}
