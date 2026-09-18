package com.manage.lotto.ml;

import com.manage.lotto.domain.LottoRules;

import java.util.stream.IntStream;

/**
 * 무작위로 고른 6개 번호가 당첨 번호와 몇 개 일치하는지에 대한 이론 확률
 * <p>
 * 번호를 추첨 전에 정했다면, 어떤 방법으로 골랐든 추첨이 공정한 한 일치 개수는 아래 분포를 따른다.
 * 그래서 모델 결과가 이 분포로는 설명하기 어려울 만큼 좋을 때만 "무작위보다 낫다"고 볼 수 있다.
 */
public final class RandomMatchStatistics {

    /** 당첨금이 있는 최소 일치 개수 (5등) */
    public static final int PRIZE_MATCHES = 3;

    /** k개 일치할 확률 (인덱스 k = 0~6, 초기하분포) */
    private static final double[] MATCH_PROBABILITIES = IntStream.rangeClosed(0, LottoRules.NUMBERS_PER_DRAW)
            .mapToDouble(k -> combination(LottoRules.NUMBERS_PER_DRAW, k)
                    * combination(LottoRules.MAX_NUMBER - LottoRules.NUMBERS_PER_DRAW, LottoRules.NUMBERS_PER_DRAW - k)
                    / combination(LottoRules.MAX_NUMBER, LottoRules.NUMBERS_PER_DRAW))
            .toArray();

    private RandomMatchStatistics() {
    }

    /**
     * k개 일치할 확률
     */
    public static double matchProbability(int k) {
        return MATCH_PROBABILITIES[k];
    }

    /**
     * 회차당 평균 일치 개수 (6 × 6 / 45 = 0.8)
     */
    public static double expectedMatches() {
        return IntStream.range(0, MATCH_PROBABILITIES.length).mapToDouble(k -> k * MATCH_PROBABILITIES[k]).sum();
    }

    /**
     * 3개 이상 일치할 확률 (약 2.38%)
     */
    public static double prizeProbability() {
        return IntStream.range(PRIZE_MATCHES, MATCH_PROBABILITIES.length).mapToDouble(k -> MATCH_PROBABILITIES[k]).sum();
    }

    /**
     * 무작위로 draws회차를 골랐을 때 일치 개수 합계가 totalMatches 이상일 확률 (단측 p값)
     */
    public static double totalMatchesPValue(int totalMatches, int draws) {
        // 회차별 일치 개수 분포를 draws번 합성해 합계의 정확한 분포를 구함
        double[] distribution = {1.0};
        for (int draw = 0; draw < draws; draw++) {
            double[] next = new double[distribution.length + LottoRules.NUMBERS_PER_DRAW];
            for (int sum = 0; sum < distribution.length; sum++) {
                for (int k = 0; k < MATCH_PROBABILITIES.length; k++) {
                    next[sum + k] += distribution[sum] * MATCH_PROBABILITIES[k];
                }
            }
            distribution = next;
        }
        return tail(distribution, totalMatches);
    }

    /**
     * 무작위로 draws회차를 골랐을 때 3개 이상 일치한 회차가 prizeDraws 이상일 확률 (단측 p값, 이항분포)
     */
    public static double prizeDrawsPValue(int prizeDraws, int draws) {
        double p = prizeProbability();
        double[] distribution = new double[draws + 1];
        distribution[0] = Math.pow(1 - p, draws);
        for (int k = 0; k < draws; k++) {
            distribution[k + 1] = distribution[k] * (draws - k) / (k + 1) * p / (1 - p);
        }
        return tail(distribution, prizeDraws);
    }

    private static double tail(double[] distribution, int from) {
        double sum = 0;
        for (int i = Math.max(0, from); i < distribution.length; i++) {
            sum += distribution[i];
        }
        return Math.min(1.0, sum);
    }

    private static double combination(int n, int k) {
        double result = 1;
        for (int i = 1; i <= k; i++) {
            result = result * (n - k + i) / i;
        }
        return result;
    }
}
