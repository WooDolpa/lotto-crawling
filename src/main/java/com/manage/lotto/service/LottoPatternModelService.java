package com.manage.lotto.service;

import com.manage.lotto.domain.LottoHistory;
import com.manage.lotto.domain.LottoRules;
import com.manage.lotto.dto.ModelStatus;
import com.manage.lotto.dto.PredictedGame;
import com.manage.lotto.exception.InvalidLottoDataException;
import com.manage.lotto.exception.ModelNotReadyException;
import com.manage.lotto.ml.HistoryFingerprint;
import com.manage.lotto.ml.LottoPatternTrainer;
import com.manage.lotto.ml.PatternModelStore;
import com.manage.lotto.ml.SavedPatternModel;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import smile.classification.RandomForest;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 운영용 A 점수 모델의 로드·재학습·예측 (클래스 이름은 이전 패턴 모델에서 유래) (학습 시점은 {@link LottoModelTrainingService}가 정함)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LottoPatternModelService {

    private static final long MODEL_SEED = 42;

    private final LottoPatternTrainer trainer;
    private final PatternModelStore store;

    private volatile SavedPatternModel current;
    private volatile String error;

    @PostConstruct
    public void load() {
        try {
            Optional<SavedPatternModel> saved = store.load();
            if (saved.isEmpty()) {
                return;
            }
            if (!LottoPatternTrainer.MODEL_VERSION.equals(saved.get().version())) {
                throw new IOException("모델 특징/설정 버전이 다릅니다.");
            }
            current = saved.get();
            log.info("점수 모델 로드 완료: 기준 {}회, {}건", current.baseDrawNo(), current.historyCount());
        } catch (Exception e) {
            error = "저장 모델을 불러오지 못했습니다.";
            log.error(error, e);
        }
    }

    /**
     * 이력이 마지막 학습 때와 다르면 전체 이력으로 다시 학습해 파일 저장 후 교체
     * (실패하면 오류만 기록하고 기존 모델 유지)
     *
     * @param histories 회차 오름차순 전체 이력
     */
    public synchronized void refresh(List<LottoHistory> histories) {
        try {
            validate(histories);
            String hash = fingerprint(histories);
            SavedPatternModel previous = current;
            if (previous != null && previous.hash().equals(hash)) {
                error = null;
                return;
            }

            log.info("전체 {}회차로 점수 모델 학습 시작", histories.size());
            RandomForest model = trainer.train(histories, MODEL_SEED);
            // Exercise inference before publishing a newly trained model.
            trainer.infer(model, histories);

            SavedPatternModel next = new SavedPatternModel(LottoPatternTrainer.MODEL_VERSION, hash,
                    histories.get(histories.size() - 1).getDrwNo(), histories.size(), Instant.now(), model);
            store.save(next);
            current = next;
            error = null;
            log.info("점수 모델 저장/교체 완료: 기준 {}회, {}건", next.baseDrawNo(), next.historyCount());
        } catch (Exception e) {
            // 메시지가 없는 예외도 상태에 오류로 보이도록 예외 이름으로 대신
            error = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            log.error("점수 모델 학습/저장 실패. 기존 모델을 유지합니다.", e);
        }
    }

    public ModelStatus status() {
        return status(current);
    }

    /**
     * 저장 모델로 다음 회차 번호 6개 예측 (학습하지 않음)
     *
     * @param histories 회차 오름차순 전체 이력
     * @throws ModelNotReadyException    모델이 없을 때
     * @throws InvalidLottoDataException 이력이 50건 미만이거나 회차가 빠져 있을 때
     */
    public PredictedGame predict(List<LottoHistory> histories) {
        SavedPatternModel model = current;
        if (model == null) {
            throw new ModelNotReadyException("점수 모델이 아직 없습니다. 학습 상태를 확인해 주세요.");
        }
        validate(histories);
        return PredictedGame.of(trainer.infer(model.model(), histories),
                !model.hash().equals(fingerprint(histories)), status(model));
    }

    private ModelStatus status(SavedPatternModel model) {
        if (model == null) {
            return ModelStatus.unavailable(error);
        }
        return new ModelStatus(true, model.baseDrawNo(), model.historyCount(),
                LottoPatternTrainer.trainingSamples(model.historyCount()), model.trainedAt(), error);
    }

    private static void validate(List<LottoHistory> histories) {
        if (histories.size() < LottoPatternTrainer.MIN_HISTORY) {
            throw new InvalidLottoDataException("최소 " + LottoPatternTrainer.MIN_HISTORY + "건의 연속된 당첨 이력이 필요합니다.");
        }
        LottoRules.requireContinuousHistory(histories);
    }

    private static String fingerprint(List<LottoHistory> histories) {
        return HistoryFingerprint.of(LottoPatternTrainer.MODEL_VERSION, histories);
    }
}
