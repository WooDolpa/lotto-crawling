package com.manage.lotto.ml;

import com.manage.lotto.domain.LottoRules;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import smile.base.cart.SplitRule;
import smile.classification.RandomForest;
import smile.data.DataFrame;
import smile.data.formula.Formula;
import smile.data.vector.IntVector;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Smile ML RandomForest를 활용하여 1~45번 공 각각의 차기 회차 출현 확률을 예측하는 컴포넌트
 */
@Slf4j
@Component
public class LottoMlPredictor {

    private static final String TARGET = "y";
    /** 0으로 나누기 방지 최소 확률 */
    private static final double MIN_PROBABILITY = 0.001;
    /** 학습 시드 (같은 이력이면 재시작해도 같은 확률) */
    private static final long SEED = 42;
    // Smile RandomForest 기본 설정과 같은 값 (트리 수, 최대 깊이, 잎 최소 크기)
    private static final int TREES = 500;
    private static final int MAX_DEPTH = 20;
    private static final int NODE_SIZE = 5;

    public record BallProbability(int ballNumber, double probability, int rank) {}

    /**
     * 학습 데이터셋으로 Random Forest 모델을 학습하고 차기 회차 1~45번 번호별 출현 확률 산출
     *
     * @param dataset 과거 회차 피처/타겟 학습 데이터
     * @param inferenceX 차기 회차 예측용 45개 공의 피처 매트릭스 (45 x 6)
     * @return 1~45번 공별 정규화된 출현 확률 (Key: ballNumber)
     * @throws IllegalArgumentException 학습 데이터가 비어 있을 때
     * @throws RuntimeException         학습·추론 실패 시 (균등 확률로 대신하지 않고 호출자에게 알림)
     */
    public Map<Integer, Double> predictProbabilities(LottoFeatureExtractor.FeatureDataset dataset, double[][] inferenceX) {
        return probabilities(train(dataset), inferenceX);
    }

    /**
     * 학습 데이터셋으로 Random Forest 모델 학습
     *
     * @throws IllegalArgumentException 학습 데이터가 비어 있을 때
     */
    public RandomForest train(LottoFeatureExtractor.FeatureDataset dataset) {
        double[][] x = dataset.x();
        int[] y = dataset.y();

        if (x.length == 0 || y.length == 0) {
            throw new IllegalArgumentException("학습 데이터가 비어 있습니다.");
        }

        DataFrame trainDf = DataFrame.of(x, LottoFeatureExtractor.FEATURE_NAMES).merge(IntVector.of(TARGET, y));

        log.info("Smile RandomForest 모델 학습 시작 (학습 샘플 수: {})", x.length);
        // 기본 설정(최대 잎 수 = 샘플 수 / 5 포함)은 그대로 두고 트리별 시드만 고정 (Smile 3.1.1은 1보다 큰 시드만 적용)
        RandomForest model = RandomForest.fit(Formula.lhs(TARGET), trainDf, TREES, 0, SplitRule.GINI, MAX_DEPTH,
                x.length / 5, NODE_SIZE, 1.0, null, new Random(SEED).longs(2, Long.MAX_VALUE).distinct().limit(TREES));
        log.info("Smile RandomForest 모델 학습 완료");
        return model;
    }

    /**
     * 학습된 모델로 1~45번 공별 출현 확률 산출 (합이 1.0이 되도록 정규화)
     *
     * @param inferenceX 45개 공의 피처 매트릭스 (45 x 6)
     */
    public Map<Integer, Double> probabilities(RandomForest model, double[][] inferenceX) {
        DataFrame infDf = DataFrame.of(inferenceX, LottoFeatureExtractor.FEATURE_NAMES);

        Map<Integer, Double> probabilities = new HashMap<>();
        double totalProb = 0.0;

        for (int i = 0; i < LottoRules.MAX_NUMBER; i++) {
            double[] posteriori = new double[2];
            model.predict(infDf.get(i), posteriori);

            // posteriori[1] = class 1 (출현) 확률
            double prob = posteriori[1];
            if (Double.isNaN(prob) || prob <= 0.0) {
                prob = MIN_PROBABILITY;
            }
            probabilities.put(i + 1, prob);
            totalProb += prob;
        }

        // 가중 샘플링을 위해 확률 정규화 (합이 1.0이 되도록)
        for (Map.Entry<Integer, Double> entry : probabilities.entrySet()) {
            entry.setValue(entry.getValue() / totalProb);
        }

        return probabilities;
    }

    /**
     * 이력이 부족할 때 쓰는 1~45번 공 균등 기본 확률 생성
     */
    public Map<Integer, Double> createFallbackProbabilities() {
        Map<Integer, Double> fallback = new HashMap<>();
        double baseProb = 1.0 / LottoRules.MAX_NUMBER;
        for (int i = LottoRules.MIN_NUMBER; i <= LottoRules.MAX_NUMBER; i++) {
            fallback.put(i, baseProb);
        }
        return fallback;
    }
}
