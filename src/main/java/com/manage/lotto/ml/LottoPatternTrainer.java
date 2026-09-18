package com.manage.lotto.ml;

import com.manage.lotto.domain.LottoHistory;
import com.manage.lotto.domain.LottoRules;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import smile.base.cart.SplitRule;
import smile.classification.RandomForest;
import smile.data.DataFrame;
import smile.data.formula.Formula;
import smile.data.vector.IntVector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.IntStream;

/**
 * A 점수 모델: 기본 6개 특징({@link LottoFeatureExtractor})으로 RandomForest를 학습하고, 점수 상위 6개 번호를 추론
 * <p>
 * 클래스·설정 이름의 "pattern"은 용지 패턴 특징을 쓰던 이전 모델에서 유래했다. 패턴 특징은 시간순 검증에서
 * 적중에 도움이 되지 않고 가장자리 번호로 쏠림만 만들어 제거했다.
 */
@Component
@RequiredArgsConstructor
public class LottoPatternTrainer {

    /** 특징 구성이나 학습 설정이 바뀌면 함께 변경 (저장 모델 호환성 판단에 사용) */
    public static final String MODEL_VERSION = "score-v3-base6-cooccurrence-rate-smile3.1.1-trees60-depth12-nodes100-leaf5-seed42";
    /** 학습에 필요한 최소 연속 회차 수 */
    public static final int MIN_HISTORY = 50;

    private static final String TARGET = "target";
    private static final int TREES = 60;
    private static final int MAX_DEPTH = 12;
    private static final int MAX_NODES = 100;
    private static final int NODE_SIZE = 5;

    private final LottoFeatureExtractor base;

    public static int trainingSamples(int historyCount) {
        return (historyCount - LottoFeatureExtractor.MIN_PAST_DRAWS) * LottoRules.MAX_NUMBER;
    }

    public RandomForest train(List<LottoHistory> histories, long seed) {
        List<double[]> rows = new ArrayList<>();
        List<Integer> labels = new ArrayList<>();
        for (int t = LottoFeatureExtractor.MIN_PAST_DRAWS; t < histories.size(); t++) {
            List<LottoHistory> past = histories.subList(0, t);
            for (int ball = LottoRules.MIN_NUMBER; ball <= LottoRules.MAX_NUMBER; ball++) {
                rows.add(features(ball, past));
                labels.add(histories.get(t).contains(ball) ? 1 : 0);
            }
        }
        return fit(rows.toArray(double[][]::new), labels.stream().mapToInt(Integer::intValue).toArray(), seed);
    }

    /**
     * 미리 만든 특징 행과 정답(1: 출현, 0: 미출현)으로 학습
     */
    public RandomForest fit(double[][] x, int[] y, long seed) {
        DataFrame train = DataFrame.of(x, featureNames(x[0].length)).merge(IntVector.of(TARGET, y));
        // Smile 3.1.1 only applies tree seeds greater than 1.
        return RandomForest.fit(Formula.lhs(TARGET), train, TREES, 0, SplitRule.GINI, MAX_DEPTH, MAX_NODES, NODE_SIZE,
                1.0, null, new Random(seed).longs(2, Long.MAX_VALUE).distinct().limit(TREES));
    }

    /**
     * past 이후 회차를 맞히기 위한 공 하나의 특징 (기본 6개)
     */
    public double[] features(int ball, List<LottoHistory> past) {
        return base.extractBallFeatures(ball, past);
    }

    /**
     * 45개 번호의 출현 점수를 계산해 상위 6개 번호를 오름차순으로 반환
     */
    public List<Integer> infer(RandomForest model, List<LottoHistory> histories) {
        double[][] inference = new double[LottoRules.MAX_NUMBER][];
        for (int ball = LottoRules.MIN_NUMBER; ball <= LottoRules.MAX_NUMBER; ball++) {
            inference[ball - 1] = features(ball, histories);
        }
        return infer(model, inference);
    }

    /**
     * 미리 만든 45개 번호의 특징(인덱스 0 = 1번 공)으로 상위 6개 번호를 오름차순으로 반환
     */
    public List<Integer> infer(RandomForest model, double[][] inference) {
        DataFrame frame = DataFrame.of(inference, featureNames(inference[0].length));

        Map<Integer, Double> scores = new HashMap<>();
        for (int i = 0; i < LottoRules.MAX_NUMBER; i++) {
            double[] posterior = new double[2];
            model.predict(frame.get(i), posterior);
            if (!Double.isFinite(posterior[1])) {
                throw new IllegalStateException("유효하지 않은 모델 점수");
            }
            scores.put(i + 1, posterior[1]);
        }
        return scores.keySet().stream()
                .sorted(Comparator.<Integer>comparingDouble(scores::get).reversed().thenComparingInt(Integer::intValue))
                .limit(LottoRules.NUMBERS_PER_DRAW)
                .sorted()
                .toList();
    }

    private static String[] featureNames(int count) {
        return IntStream.range(0, count).mapToObj(i -> "f" + i).toArray(String[]::new);
    }
}
