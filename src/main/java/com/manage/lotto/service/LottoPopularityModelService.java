package com.manage.lotto.service;

import com.manage.lotto.domain.LottoHistory;
import com.manage.lotto.dto.ModelStatus;
import com.manage.lotto.dto.PredictedGame;
import com.manage.lotto.exception.ModelNotReadyException;
import com.manage.lotto.ml.HistoryFingerprint;
import com.manage.lotto.ml.PopularityPredictor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Random;

/**
 * E 인기도 모델: 조합의 인기도를 학습해 두고, 덜 붐비는 조합으로 게임 생성
 * (학습 시점은 {@link LottoModelTrainingService}가 정함)
 * <p>
 * 학습 결과는 메모리에만 둔다. 회귀 계수 몇 개뿐이라 다시 학습해도 1초가 걸리지 않으므로
 * A처럼 파일로 저장할 이유가 없다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LottoPopularityModelService {

    /** 이력 지문 계산용 버전 (특징이나 학습 방식이 바뀌면 함께 변경) */
    private static final String MODEL_VERSION = "popularity-v2-ridge9-fifth";

    private final PopularityPredictor predictor;
    private final Random random = new SecureRandom();

    /**
     * 학습 결과
     *
     * @param hash         학습에 사용한 이력 지문
     * @param baseDrawNo   학습에 사용한 마지막 회차
     * @param historyCount 학습 시점의 전체 이력 건수
     * @param model        인기도 회귀 모델
     */
    private record Trained(String hash, Integer baseDrawNo, int historyCount, Instant trainedAt,
                           PopularityPredictor.Model model) {}

    private volatile Trained current;
    private volatile String error;

    /**
     * 이력이 마지막 학습 때와 다르면 인기도 모델을 다시 학습
     * (실패하면 오류만 기록하고 기존 모델 유지)
     *
     * @param histories 회차 오름차순 전체 이력
     */
    public synchronized void refresh(List<LottoHistory> histories) {
        try {
            if (histories.isEmpty()) {
                return;
            }
            String hash = fingerprint(histories);
            Trained previous = current;
            if (previous != null && previous.hash().equals(hash)) {
                error = null;
                return;
            }

            PopularityPredictor.Model model = predictor.train(histories);
            current = new Trained(hash, histories.get(histories.size() - 1).getDrwNo(), histories.size(),
                    Instant.now(), model);
            error = null;
            log.info("인기도 모델 교체 완료: 기준 {}회, 인기도를 계산한 회차 {}건, 평균 지수 {}",
                    current.baseDrawNo(), model.samples(), String.format("%.3f", model.averageIndex()));
        } catch (Exception e) {
            // 메시지가 없는 예외도 상태에 오류로 보이도록 예외 이름으로 대신
            error = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            log.error("인기도 모델 학습 실패. 기존 모델을 유지합니다.", e);
        }
    }

    /**
     * 학습된 인기도로 덜 붐비는 1게임 생성 (학습하지 않음, 호출마다 번호가 달라질 수 있음)
     *
     * @param histories 회차 오름차순 전체 이력 (학습 이후 변경 여부 확인용)
     * @throws ModelNotReadyException 아직 학습되지 않았을 때
     */
    public PredictedGame predict(List<LottoHistory> histories) {
        Trained trained = current;
        if (trained == null) {
            throw new ModelNotReadyException(error != null ? error
                    : "인기도 모델이 아직 학습되지 않았습니다. 학습 상태를 확인해 주세요.");
        }
        // 만들려는 게임의 직전 회차는 지금 이력의 맨 끝이다 (겹침 특징에 쓴다)
        List<Integer> previousNumbers = histories.get(histories.size() - 1).getNumbers();
        List<Integer> numbers = predictor.generate(trained.model(), random, previousNumbers);
        // 평균 대비 몇 %인지로 보여 주려고 평균 지수로 나눈다 (1.0이 평균만큼 붐비는 조합)
        double relativePopularity = trained.model().popularityOf(numbers, previousNumbers)
                / trained.model().averageIndex();
        return PredictedGame.of(numbers, !trained.hash().equals(fingerprint(histories)), status(trained),
                relativePopularity);
    }

    public ModelStatus status() {
        return status(current);
    }

    private ModelStatus status(Trained trained) {
        if (trained == null) {
            return ModelStatus.unavailable(error);
        }
        return new ModelStatus(true, trained.baseDrawNo(), trained.historyCount(), trained.model().samples(),
                trained.trainedAt(), error);
    }

    private static String fingerprint(List<LottoHistory> histories) {
        return HistoryFingerprint.ofPopularity(MODEL_VERSION, histories);
    }
}
