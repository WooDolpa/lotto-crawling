package com.manage.lotto.service;

import com.manage.lotto.domain.LottoHistory;
import com.manage.lotto.dto.ModelStatus;
import com.manage.lotto.dto.PredictedGame;
import com.manage.lotto.dto.PredictionResponse;
import com.manage.lotto.dto.PredictionStatusResponse;
import com.manage.lotto.exception.InvalidLottoDataException;
import com.manage.lotto.exception.ModelNotReadyException;
import com.manage.lotto.ml.CoOccurrenceGameGenerator;
import com.manage.lotto.repository.LottoHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

/**
 * 번호 예측: 점수 모델(A), v1 확률 모델(B), 동반출현 3개(C)·4개(D), 인기도 모델(E)로 다음 회차 번호를 1게임씩 생성
 * <p>
 * C·D는 사람들이 많이 고르는 쪽, E는 덜 고르는 쪽이다. 다섯 게임의 당첨 확률은 모두 같다.
 */
@Service
@RequiredArgsConstructor
public class LottoPredictionService {

    /** C가 고정하는 번호 수 */
    private static final int CO_OCCURRENCE_TRIPLE = 3;
    /** D가 고정하는 번호 수 */
    private static final int CO_OCCURRENCE_QUAD = 4;

    private final LottoHistoryRepository repository;
    private final LottoPatternModelService patternModel;
    private final LottoRecommendationService probabilityModel;
    private final LottoModelTrainingService training;
    private final CoOccurrenceGameGenerator coOccurrenceGenerator;
    private final LottoPopularityModelService popularityModel;

    private final Random random = new SecureRandom();

    public PredictionStatusResponse status() {
        return new PredictionStatusResponse(training.isTraining(), patternModel.status(), probabilityModel.status(),
                popularityModel.status());
    }

    /**
     * 학습 모델의 변경 확인·재학습을 백그라운드로 요청하고 현재 상태 반환
     */
    public PredictionStatusResponse requestTraining() {
        training.requestRefresh();
        return status();
    }

    /**
     * A·B·E 모델과 C·D 규칙으로 1게임씩 생성 (학습하지 않음). 한 모델이 예측하지 못해도 나머지 결과는 반환한다.
     *
     * @throws InvalidLottoDataException 저장된 이력이 없을 때
     */
    public PredictionResponse predict() {
        List<LottoHistory> histories = repository.findAllByOrderByDrwNoAsc();
        if (histories.isEmpty()) {
            throw new InvalidLottoDataException("저장된 당첨 이력이 없습니다. 데이터를 먼저 등록해 주세요.");
        }
        LottoHistory latest = histories.get(histories.size() - 1);
        PredictedGame pattern = predict(() -> patternModel.predict(histories), patternModel::status);
        PredictedGame probability = predict(() -> probabilityModel.predict(histories), probabilityModel::status);
        PredictedGame triple = PredictedGame.of(
                coOccurrenceGenerator.generate(CO_OCCURRENCE_TRIPLE, histories, random), false, null);
        PredictedGame quad = PredictedGame.of(
                coOccurrenceGenerator.generate(CO_OCCURRENCE_QUAD, histories, random), false, null);
        PredictedGame popularity = predict(() -> popularityModel.predict(histories), popularityModel::status);
        return new PredictionResponse(latest.getDrwNo(), latest.getDrwNo() + 1, pattern, probability, triple, quad,
                popularity);
    }

    private static PredictedGame predict(Supplier<PredictedGame> prediction, Supplier<ModelStatus> status) {
        try {
            return prediction.get();
        } catch (ModelNotReadyException | InvalidLottoDataException e) {
            return PredictedGame.unavailable(e.getMessage(), status.get());
        }
    }
}
