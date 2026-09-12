package com.manage.lotto.ml;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import smile.classification.RandomForest;
import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.formula.Formula;
import smile.data.vector.DoubleVector;
import smile.data.vector.IntVector;

import java.util.*;

/**
 * Smile ML RandomForest를 활용하여 1~45번 공 각각의 차기 회차 출현 확률을 예측하는 컴포넌트
 */
@Slf4j
@Component
public class LottoMlPredictor {

    public record BallProbability(int ballNumber, double probability, int rank) {}

    /**
     * 학습 데이터셋으로 Random Forest 모델을 학습하고 차기 회차 1~45번 번호별 출현 확률 산출
     *
     * @param dataset 과거 회차 피처/타겟 학습 데이터
     * @param inferenceX 차기 회차 예측용 45개 공의 피처 매트릭스 (45 x 6)
     * @return 1~45번 공별 출현 확률 및 순위 정보 매핑 (Key: ballNumber)
     */
    public Map<Integer, Double> predictProbabilities(LottoFeatureExtractor.FeatureDataset dataset, double[][] inferenceX) {
        double[][] x = dataset.x();
        int[] y = dataset.y();

        if (x.length == 0 || y.length == 0) {
            log.warn("학습 데이터가 비어 있어 균등 기본 확률(1/45)을 반환합니다.");
            return createFallbackProbabilities();
        }

        try {
            int n = x.length;
            double[] f1 = new double[n];
            double[] f2 = new double[n];
            double[] f3 = new double[n];
            double[] f4 = new double[n];
            double[] f5 = new double[n];
            double[] f6 = new double[n];

            for (int i = 0; i < n; i++) {
                f1[i] = x[i][0];
                f2[i] = x[i][1];
                f3[i] = x[i][2];
                f4[i] = x[i][3];
                f5[i] = x[i][4];
                f6[i] = x[i][5];
            }

            DataFrame trainDf = DataFrame.of(
                    DoubleVector.of("freq_5", f1),
                    DoubleVector.of("freq_10", f2),
                    DoubleVector.of("freq_20", f3),
                    DoubleVector.of("absence_streak", f4),
                    DoubleVector.of("co_occurrence", f5),
                    DoubleVector.of("total_rate", f6),
                    IntVector.of("y", y)
            );

            Formula formula = Formula.lhs("y");
            log.info("Smile RandomForest 모델 학습 시작 (학습 샘플 수: {})", n);
            RandomForest model = RandomForest.fit(formula, trainDf);
            log.info("Smile RandomForest 모델 학습 완료");

            // 차기 45개 공에 대한 추론 DataFrame 생성
            int m = inferenceX.length;
            double[] inf1 = new double[m];
            double[] inf2 = new double[m];
            double[] inf3 = new double[m];
            double[] inf4 = new double[m];
            double[] inf5 = new double[m];
            double[] inf6 = new double[m];

            for (int i = 0; i < m; i++) {
                inf1[i] = inferenceX[i][0];
                inf2[i] = inferenceX[i][1];
                inf3[i] = inferenceX[i][2];
                inf4[i] = inferenceX[i][3];
                inf5[i] = inferenceX[i][4];
                inf6[i] = inferenceX[i][5];
            }

            DataFrame infDf = DataFrame.of(
                    DoubleVector.of("freq_5", inf1),
                    DoubleVector.of("freq_10", inf2),
                    DoubleVector.of("freq_20", inf3),
                    DoubleVector.of("absence_streak", inf4),
                    DoubleVector.of("co_occurrence", inf5),
                    DoubleVector.of("total_rate", inf6)
            );

            Map<Integer, Double> probabilities = new HashMap<>();
            double totalProb = 0.0;

            for (int i = 0; i < 45; i++) {
                Tuple tuple = infDf.get(i);
                double[] posteriori = new double[2];
                model.predict(tuple, posteriori);

                int ballNumber = i + 1;
                // posteriori[1] = class 1 (출현) 확률
                double prob = posteriori.length > 1 ? posteriori[1] : 0.0;
                if (Double.isNaN(prob) || prob <= 0.0) {
                    prob = 0.001; // 0으로 나누기 방지 최소값
                }
                probabilities.put(ballNumber, prob);
                totalProb += prob;
            }

            // 가중 샘플링을 위해 확률 정규화 (합이 1.0이 되도록)
            if (totalProb > 0) {
                for (Map.Entry<Integer, Double> entry : probabilities.entrySet()) {
                    probabilities.put(entry.getKey(), entry.getValue() / totalProb);
                }
            }

            return probabilities;

        } catch (Exception e) {
            log.error("머신러닝 모델 학습 및 예측 중 오류 발생: {}", e.getMessage(), e);
            return createFallbackProbabilities();
        }
    }

    /**
     * 예외 상황 발생 시 1~45번 공 균등 기본 확률 생성
     */
    public Map<Integer, Double> createFallbackProbabilities() {
        Map<Integer, Double> fallback = new HashMap<>();
        double baseProb = 1.0 / 45.0;
        for (int i = 1; i <= 45; i++) {
            fallback.put(i, baseProb);
        }
        return fallback;
    }
}
