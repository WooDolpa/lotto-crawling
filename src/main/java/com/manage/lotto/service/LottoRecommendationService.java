package com.manage.lotto.service;

import com.manage.lotto.domain.LottoHistory;
import com.manage.lotto.dto.GameRecommendationDto;
import com.manage.lotto.dto.LottoRecommendResponse;
import com.manage.lotto.dto.ModelStatus;
import com.manage.lotto.dto.PredictedGame;
import com.manage.lotto.exception.ModelNotReadyException;
import com.manage.lotto.ml.HistoryFingerprint;
import com.manage.lotto.ml.LottoFeatureExtractor;
import com.manage.lotto.ml.LottoGameGenerator;
import com.manage.lotto.ml.LottoMlPredictor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * v1 확률 모델: 번호별 출현 확률을 학습해 두고, 확률 가중 샘플링으로 게임 생성
 * (학습 시점은 {@link LottoModelTrainingService}가 정함)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LottoRecommendationService {

    /** 이력 지문 계산용 버전 (특징이나 학습 방식이 바뀌면 함께 변경) */
    private static final String MODEL_VERSION = "recommend-v1.1-cooccurrence-rate-seed42";

    private final LottoFeatureExtractor featureExtractor;
    private final LottoMlPredictor mlPredictor;
    private final LottoGameGenerator gameGenerator;

    /**
     * 학습 결과
     *
     * @param hash            학습에 사용한 이력 지문
     * @param baseDrawNo      학습에 사용한 마지막 회차 (이력이 없으면 null)
     * @param trainingSamples 학습 샘플 수 (이력이 부족해 균등 확률을 쓰면 0)
     * @param probabilities   1~45번 번호별 출현 확률 (합계 1)
     */
    private record TrainedProbabilities(String hash, Integer baseDrawNo, int historyCount, int trainingSamples,
                                        Instant trainedAt, Map<Integer, Double> probabilities) {}

    private volatile TrainedProbabilities current;
    private volatile String error;

    /**
     * 학습된 확률로 5게임 추천 (학습하지 않음)
     *
     * @throws ModelNotReadyException 아직 학습되지 않았을 때
     */
    public LottoRecommendResponse recommend5Games() {
        TrainedProbabilities trained = requireTrained();
        List<GameRecommendationDto> games = gameGenerator.generate(trained.probabilities());
        return new LottoRecommendResponse("SUCCESS", "Smile ML 기반 5게임 로또 번호 추천 완료", trained.baseDrawNo(), games);
    }

    /**
     * 학습된 확률로 다음 회차 1게임 생성 (학습하지 않음, 호출마다 번호가 달라질 수 있음)
     *
     * @param histories 회차 오름차순 전체 이력 (학습 이후 변경 여부 확인용)
     * @throws ModelNotReadyException 아직 학습되지 않았을 때
     */
    public PredictedGame predict(List<LottoHistory> histories) {
        TrainedProbabilities trained = requireTrained();
        List<Integer> numbers = gameGenerator.generate(trained.probabilities(), 1).get(0).numbers();
        return PredictedGame.of(numbers, !trained.hash().equals(fingerprint(histories)), status(trained));
    }

    /**
     * 이력이 마지막 학습 때와 다르면 번호별 출현 확률을 다시 학습
     * (이력이 25회 미만이면 균등 확률, 실패하면 오류만 기록하고 기존 확률 유지)
     *
     * @param histories 회차 오름차순 전체 이력
     */
    public synchronized void refresh(List<LottoHistory> histories) {
        try {
            String hash = fingerprint(histories);
            TrainedProbabilities previous = current;
            if (previous != null && previous.hash().equals(hash)) {
                error = null;
                return;
            }

            Integer latestDrwNo = histories.isEmpty() ? null : histories.get(histories.size() - 1).getDrwNo();
            Map<Integer, Double> probabilities;
            int samples;
            if (histories.size() >= LottoFeatureExtractor.MIN_TRAINING_DRAWS) {
                log.info("확률 모델 학습 시작 (기준 회차: {}회, 총 {}건)", latestDrwNo, histories.size());
                LottoFeatureExtractor.FeatureDataset dataset = featureExtractor.extractTrainingDataset(histories);
                double[][] inferenceX = featureExtractor.extractInferenceFeatures(histories);
                probabilities = mlPredictor.predictProbabilities(dataset, inferenceX);
                samples = dataset.y().length;
            } else {
                log.warn("DB에 저장된 회차 데이터가 {}회 미만({}건)입니다. 기본 확률 가중치를 적용합니다.",
                        LottoFeatureExtractor.MIN_TRAINING_DRAWS, histories.size());
                probabilities = mlPredictor.createFallbackProbabilities();
                samples = 0;
            }

            current = new TrainedProbabilities(hash, latestDrwNo, histories.size(), samples, Instant.now(), Map.copyOf(probabilities));
            error = null;
            log.info("확률 모델 학습 완료 (기준 회차: {}회, 총 {}건)", latestDrwNo, histories.size());
        } catch (Exception e) {
            // 메시지가 없는 예외도 상태에 오류로 보이도록 예외 이름으로 대신
            error = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            log.error("확률 모델 학습 실패. 기존 확률을 유지합니다.", e);
        }
    }

    public ModelStatus status() {
        return status(current);
    }

    private TrainedProbabilities requireTrained() {
        TrainedProbabilities trained = current;
        if (trained == null) {
            throw new ModelNotReadyException("확률 모델이 아직 학습되지 않았습니다. 학습 상태를 확인해 주세요.");
        }
        return trained;
    }

    private ModelStatus status(TrainedProbabilities trained) {
        if (trained == null) {
            return ModelStatus.unavailable(error);
        }
        return new ModelStatus(true, trained.baseDrawNo(), trained.historyCount(), trained.trainingSamples(),
                trained.trainedAt(), error);
    }

    private static String fingerprint(List<LottoHistory> histories) {
        return HistoryFingerprint.of(MODEL_VERSION, histories);
    }
}
