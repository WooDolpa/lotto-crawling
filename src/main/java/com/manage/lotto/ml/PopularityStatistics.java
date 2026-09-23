package com.manage.lotto.ml;

import smile.stat.distribution.TDistribution;

import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

/**
 * 인기도 예측이 실제와 얼마나 맞았는지 재는 통계
 * <p>
 * 적중률 검증({@link RandomMatchStatistics})과 목적이 다르다. E 모델이 노리는 것은 당첨 확률이 아니라
 * "당첨됐을 때 당첨금을 몇 명과 나누는가"이므로, 맞힌 개수가 아니라 <b>예측한 인기도와 실제 인기도가
 * 같이 움직였는지</b>를 본다.
 * <p>
 * 생성한 조합의 실제 인기도는 잴 방법이 없다 (그 조합이 당첨되지 않았으니 5등 당첨자 수도 없다).
 * 그래서 대신 <b>그 회차의 실제 당첨 조합</b>의 인기도를 직전 이력만으로 학습한 모델이 얼마나 맞히는지
 * 잰다. 당첨 조합은 무작위로 정해지므로 조합 공간에서 고르게 뽑힌 표본이고, 모델이 처음 보는 조합이다.
 */
public final class PopularityStatistics {

    /** 통계를 낼 수 있는 최소 회차 수 */
    public static final int MIN_DRAWS = 20;
    /** 상·하위로 나눠 비교하는 비율 */
    private static final double QUARTILE = 0.25;

    private PopularityStatistics() {
    }

    /**
     * 검증 결과
     *
     * @param correlation        예측 인기도와 실제 인기도의 상관계수 (1에 가까울수록 잘 맞힘)
     * @param slope              실제 = a + slope × 예측 의 기울기. 1보다 작으면 예측이 낙관적이라는 뜻이다.
     * @param pValue             사실은 아무 관계가 없는데 우연히 이만큼 상관이 나올 확률 (단측)
     * @param lowQuartileIndex   덜 붐빌 것으로 예측한 하위 25% 회차의 실제 평균 인기도
     * @param highQuartileIndex  더 붐빌 것으로 예측한 상위 25% 회차의 실제 평균 인기도
     */
    public record Accuracy(double correlation, double slope, double pValue,
                           double lowQuartileIndex, double highQuartileIndex) {}

    /**
     * 예측-실제 쌍으로 상관계수·기울기·사분위 비교를 계산
     *
     * @param predicted 직전 이력만으로 학습한 모델이 예측한 인기도 지수
     * @param actual    같은 회차의 실제 인기도 지수 (predicted와 같은 순서)
     * @throws IllegalArgumentException 회차 수가 {@value #MIN_DRAWS}건 미만일 때
     */
    public static Accuracy of(double[] predicted, double[] actual) {
        if (predicted.length < MIN_DRAWS || predicted.length != actual.length) {
            throw new IllegalArgumentException("인기도 검증에는 최소 " + MIN_DRAWS + "회차가 필요합니다.");
        }
        int n = predicted.length;
        double meanPredicted = average(predicted);
        double meanActual = average(actual);
        double covariance = 0;
        double predictedVariance = 0;
        double actualVariance = 0;
        for (int i = 0; i < n; i++) {
            double dx = predicted[i] - meanPredicted;
            double dy = actual[i] - meanActual;
            covariance += dx * dy;
            predictedVariance += dx * dx;
            actualVariance += dy * dy;
        }
        double correlation = covariance / Math.sqrt(predictedVariance * actualVariance);
        double slope = covariance / predictedVariance;

        // 예측이 낮은 순으로 줄 세워 양 끝 25%의 실제 인기도를 견줌 (상관계수보다 읽기 쉬운 형태)
        List<Integer> order = IntStream.range(0, n).boxed()
                .sorted(Comparator.comparingDouble(i -> predicted[i]))
                .toList();
        int size = Math.max(1, (int) Math.round(n * QUARTILE));
        double low = average(order.subList(0, size), actual);
        double high = average(order.subList(n - size, n), actual);
        return new Accuracy(correlation, slope, pValue(correlation, n), low, high);
    }

    /**
     * 상관계수가 0이라는 가정 아래 이만큼 이상 나올 확률 (단측)
     * <p>
     * t = r√(n-2) / √(1-r²), 자유도 n-2의 t 분포를 쓴다. 상관이 0 이하면 검정할 것이 없어 1을 반환한다.
     */
    private static double pValue(double correlation, int n) {
        if (correlation <= 0) {
            return 1;
        }
        if (correlation >= 1) {
            return 0;
        }
        double t = correlation * Math.sqrt(n - 2) / Math.sqrt(1 - correlation * correlation);
        return 1 - new TDistribution(n - 2).cdf(t);
    }

    private static double average(double[] values) {
        double total = 0;
        for (double value : values) {
            total += value;
        }
        return total / values.length;
    }

    private static double average(List<Integer> indexes, double[] values) {
        double total = 0;
        for (int index : indexes) {
            total += values[index];
        }
        return total / indexes.size();
    }
}
