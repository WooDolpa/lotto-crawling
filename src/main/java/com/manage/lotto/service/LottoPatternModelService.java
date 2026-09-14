package com.manage.lotto.service;

import com.manage.lotto.domain.LottoHistory;
import com.manage.lotto.repository.LottoHistoryRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import smile.classification.RandomForest;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Slf4j
@EnableScheduling
public class LottoPatternModelService {
    private static final String VERSION = "pattern-v1-smile3.1.1-trees60-depth12-nodes100-leaf5-seed42";
    private final LottoHistoryRepository repository;
    private final LottoPatternExperimentService trainer;
    private final Path file;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "pattern-training"); t.setDaemon(true); return t; });
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicBoolean dirty = new AtomicBoolean();
    private volatile SavedModel current;
    private volatile String error;
    private volatile boolean closed;

    private record SavedModel(String version, String hash, int baseDrawNo, int historyCount,
                              Instant trainedAt, RandomForest model) implements Serializable {}
    public record Status(boolean modelAvailable, boolean training, Integer trainedBaseDrawNo,
                         Integer historyCount, Instant trainedAt, String lastError) {}
    public record Prediction(int baseDrawNo, int nextDrawNo, int trainedBaseDrawNo, int historyCount,
                             int trainingSamples, boolean staleModel, Instant trainedAt, List<Integer> numbers) {}

    public LottoPatternModelService(LottoHistoryRepository repository, LottoPatternExperimentService trainer,
            @Value("${lotto.pattern.model-file:models/lotto-pattern.bin}") String file) {
        this.repository = repository; this.trainer = trainer; this.file = Path.of(file).toAbsolutePath();
    }

    @PostConstruct
    public void load() {
        if (!Files.exists(file)) return;
        try (ObjectInputStream input = new ObjectInputStream(Files.newInputStream(file))) {
            SavedModel saved = (SavedModel) input.readObject();
            if (!VERSION.equals(saved.version())) throw new IOException("모델 특징/설정 버전이 다릅니다.");
            current = saved;
            log.info("패턴 모델 로드 완료: 기준 {}회, {}건", saved.baseDrawNo(), saved.historyCount());
        } catch (Exception e) {
            error = "저장 모델을 불러오지 못했습니다.";
            log.error(error, e);
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startup() { requestRefresh(); }

    @TransactionalEventListener
    public void onHistoryChanged(LottoHistoryChanged event) { requestRefresh(); }

    // Also detects committed changes made directly in DB; unchanged data does not train.
    @Scheduled(fixedDelayString = "${lotto.pattern.check-interval-ms:60000}", initialDelayString = "${lotto.pattern.check-interval-ms:60000}")
    public void checkForChanges() { requestRefresh(); }

    public void requestRefresh() {
        if (closed) return;
        dirty.set(true);
        if (!running.compareAndSet(false, true)) return;
        try { worker.execute(() -> {
            try {
                do {
                    dirty.set(false);
                    try { refresh(); }
                    catch (Exception e) { error = e.getMessage(); log.error("패턴 모델 학습/저장 실패. 기존 모델을 유지합니다.", e); }
                } while (dirty.get() && !closed);
            } finally {
                running.set(false);
                if (dirty.get() && !closed) requestRefresh();
            }
        }); } catch (RejectedExecutionException e) {
            running.set(false);
            if (!closed) throw e;
        }
    }

    // Worker is single-threaded. Public for standalone integration checks.
    public synchronized void refresh() throws Exception {
        List<LottoHistory> histories = repository.findAllByOrderByDrwNoAsc();
        validate(histories);
        String hash = fingerprint(histories);
        SavedModel previous = current;
        if (previous != null && previous.hash().equals(hash)) { error = null; return; }
        log.info("전체 {}회차로 패턴 모델 학습 시작", histories.size());
        RandomForest model = trainer.train(histories, true, 42);
        // Exercise inference before publishing a newly trained model.
        trainer.infer(model, histories, true);
        SavedModel next = new SavedModel(VERSION, hash, histories.get(histories.size() - 1).getDrwNo(), histories.size(), Instant.now(), model);
        Files.createDirectories(file.getParent());
        Path temp = Files.createTempFile(file.getParent(), "lotto-pattern-", ".tmp");
        try {
            try (ObjectOutputStream output = new ObjectOutputStream(Files.newOutputStream(temp))) { output.writeObject(next); }
            // Keep the previous file if the filesystem cannot perform an atomic replacement.
            Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            current = next; error = null;
        } finally { Files.deleteIfExists(temp); }
        log.info("패턴 모델 저장/교체 완료: 기준 {}회, {}건", next.baseDrawNo(), next.historyCount());
    }

    public Status status() {
        SavedModel model = current;
        return new Status(model != null, running.get(), model == null ? null : model.baseDrawNo(),
                model == null ? null : model.historyCount(), model == null ? null : model.trainedAt(), error);
    }

    public Prediction predict() {
        SavedModel model = current;
        if (model == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "사용 가능한 모델이 없습니다. 학습 상태를 확인해 주세요.");
        List<LottoHistory> histories = repository.findAllByOrderByDrwNoAsc();
        validate(histories);
        int base = histories.get(histories.size() - 1).getDrwNo();
        return new Prediction(base, base + 1, model.baseDrawNo(), model.historyCount(),
                (model.historyCount() - 20) * 45, !model.hash().equals(fingerprint(histories)), model.trainedAt(), trainer.infer(model.model(), histories, true));
    }

    private static void validate(List<LottoHistory> histories) {
        if (histories.size() < 50) throw new IllegalArgumentException("최소 50건의 연속된 당첨 이력이 필요합니다.");
        for (int i = 0; i < histories.size(); i++) {
            LottoHistory h = histories.get(i);
            List<Integer> n = h.getNumbers();
            if (h.getDrwNo() <= 0 || new HashSet<>(n).size() != 6 || n.stream().anyMatch(v -> v < 1 || v > 45) ||
                    (i > 0 && h.getDrwNo() != histories.get(i - 1).getDrwNo() + 1))
                throw new IllegalArgumentException("회차 누락 또는 유효하지 않은 당첨 번호가 있습니다.");
        }
    }

    private static String fingerprint(List<LottoHistory> histories) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(VERSION.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            for (LottoHistory h : histories) digest.update((h.getDrwNo() + ":" + h.getNumbers() + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    @PreDestroy
    public void close() { closed = true; worker.shutdownNow(); }
}
