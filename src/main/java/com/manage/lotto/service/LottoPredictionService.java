package com.manage.lotto.service;

import com.manage.lotto.domain.LottoHistory;
import com.manage.lotto.dto.ModelStatus;
import com.manage.lotto.dto.PredictedGame;
import com.manage.lotto.dto.PredictionResponse;
import com.manage.lotto.dto.PredictionStatusResponse;
import com.manage.lotto.exception.InvalidLottoDataException;
import com.manage.lotto.exception.ModelNotReadyException;
import com.manage.lotto.ml.RandomGameGenerator;
import com.manage.lotto.ml.UnpopularGameGenerator;
import com.manage.lotto.repository.LottoHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Supplier;

/**
 * 번호 예측: 점수 모델(A), v1 확률 모델(B), 인기 조합 제외 규칙(C), 인기도 모델(D), 무작위 기준선(E)으로 다음 회차 번호를 1게임씩 생성
 */
@Service
@RequiredArgsConstructor
public class LottoPredictionService {

    private final LottoHistoryRepository repository;
    private final LottoPatternModelService patternModel;
    private final LottoRecommendationService probabilityModel;
    private final LottoModelTrainingService training;
    private final UnpopularGameGenerator unpopularGenerator;
    private final LottoPopularityModelService popularityModel;
    private final RandomGameGenerator randomGenerator;

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
     * A·B·D 모델과 C·E 규칙으로 1게임씩 생성 (학습하지 않음). 한 모델이 예측하지 못해도 나머지 결과는 반환한다.
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
        PredictedGame unpopular = PredictedGame.of(unpopularGenerator.generate(), false, null);
        PredictedGame popularity = predict(() -> popularityModel.predict(histories), popularityModel::status);
        PredictedGame random = PredictedGame.of(randomGenerator.generate(), false, null);
        return new PredictionResponse(latest.getDrwNo(), latest.getDrwNo() + 1, pattern, probability, unpopular,
                popularity, random);
    }

    private static PredictedGame predict(Supplier<PredictedGame> prediction, Supplier<ModelStatus> status) {
        try {
            return prediction.get();
        } catch (ModelNotReadyException | InvalidLottoDataException e) {
            return PredictedGame.unavailable(e.getMessage(), status.get());
        }
    }
}
