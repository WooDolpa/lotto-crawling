package com.manage.lotto.ml;

import com.manage.lotto.domain.LottoHistory;
import com.manage.lotto.domain.LottoRules;
import com.manage.lotto.domain.PrizeRank;
import com.manage.lotto.exception.InvalidLottoDataException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import smile.data.DataFrame;
import smile.data.formula.Formula;
import smile.data.vector.DoubleVector;
import smile.regression.LinearModel;
import smile.regression.RidgeRegression;

import java.util.List;
import java.util.Random;

/**
 * 조합의 "인기도"를 학습해 덜 붐비는 조합을 뽑는 모델 (D)
 * <p>
 * 인기도 지수 = 실제 5등 당첨자 수 ÷ 기대 5등 당첨자 수. 1.0이면 평균만큼 붐빈 조합,
 * 1.2면 같은 당첨금을 20% 더 많은 사람과 나눈 조합이다. 당첨 확률은 어느 조합이든 같으므로
 * 이 모델이 올리려는 것은 적중률이 아니라 당첨됐을 때 손에 쥐는 금액이다.
 * <p>
 * <b>5등을 쓰는 이유</b>: 1등 당첨자는 회차당 평균 8명뿐이라 인기도 차이가 우연에 묻힌다.
 * 5등은 회차당 수십만 명이라 신호가 1등의 100배 이상 또렷하다.
 * <p>
 * <b>한계</b>: 5등은 당첨 조합과 3개가 겹치는 조합을 산 사람 수이므로, 엄밀히는 당첨 조합 자체가
 * 아니라 그 <i>주변</i>이 얼마나 붐볐는지를 잰다. 붐비는 번호대를 피한다는 목적에는 맞지만,
 * 조합 하나의 인기도를 정확히 맞히는 값은 아니다.
 * <p>
 * 학습 표본은 회차 수뿐이고 특징 간 상관이 크므로 트리 모델이 아니라 능선 회귀를 쓴다.
 * <p>
 * <b>실측</b>: 워크포워드 검증(641회차를 각각 직전 600회로만 학습해 예측)에서 예측-실제 상관계수 0.57,
 * 보정 기울기 0.73이다. 덜 붐빌 것으로 예측한 하위 25% 회차의 실제 인기도는 0.971, 상위 25%는 1.035다.
 * <p>
 * <b>예측값은 조금 낙관적이다.</b> 기울기가 1보다 작아 예측이 그대로 실현되지는 않는다. 이 모델이 뽑는
 * 조합의 예측 인기도는 0.94 안팎이지만 기대할 수 있는 실제 이득은 <b>약 5%</b>다 (같은 모양의 실제 회차
 * 실측값과 일치). 화면에 보여 주는 값은 예측값이므로 이 차이를 감안해서 읽어야 한다.
 * <p>
 * <b>계수를 하나씩 해석하지 말 것</b>: 특징끼리 상관이 커서(합계와 31 이하 개수 등) 개별 계수의 부호가
 * 뒤집힐 수 있다. 이 모델은 후보 조합의 순위를 매기는 데만 쓴다.
 */
@Slf4j
@Component
public class PopularityPredictor {

    /** 학습에 필요한 최소 회차 수 (판매금액·5등 당첨자 수가 모두 있는 회차 기준) */
    public static final int MIN_DRAWS = 100;
    /**
     * 학습에 쓰는 최근 회차 수
     * <p>
     * 초기 200회차(2002~2005)는 인기도 지수의 평균이 0.78, 표준편차가 이후 구간의 6배로 사실상 다른 시장이다.
     * 이 구간을 함께 학습하면 계수가 0 쪽으로 눌려 검증 상관계수가 0.66에서 0.62로 떨어진다.
     * 300~800회 사이에서는 성능 차이가 없어 가운데 값을 골랐다. 사람들이 번호를 고르는 방식이 다시 바뀌어도
     * 최근 구간만 보면 저절로 따라간다.
     */
    private static final int TRAINING_WINDOW = 600;

    /** 5등(3개 일치) 조합 수 = C(6,3) × C(39,3) */
    private static final double FIFTH_COMBINATIONS = 182_780;
    /** 1게임 가격 (원). 판매금액을 게임 수로 바꾸는 데 쓴다. */
    private static final double GAME_PRICE = 1_000;
    /** 능선 회귀 규제 강도 (특징을 표준화한 뒤 적용) */
    private static final double LAMBDA = 1.0;
    /** 표준편차가 0인 특징을 나눌 때 쓰는 대체값 */
    private static final double MIN_DEVIATION = 1e-9;
    /**
     * 게임 1개를 만들 때 견줘 보는 후보 조합 수
     * <p>
     * 전체 조합에서 최솟값을 찾지 않는 이유는 회귀식의 최솟값이 하나로 고정돼 매번 같은 조합만 나오기 때문이다.
     * 후보를 추려 그중 가장 나은 것을 고르면 호출마다 번호가 달라진다.
     * <p>
     * 200개로 뽑으면 번호 합계가 평균 203인 조합이 나온다. 무작위 기대값(138)보다 훨씬 높은데, 사람들이
     * 생일 때문에 낮은 번호를 고르므로 붐비는 곳을 피하면 필연적으로 높은 번호로 간다. 합계가 높은 구간에서
     * 효과가 꺾이지 않는지 실측으로 확인했다 (1,041회차, 201회 이후):
     * <pre>
     *   합계 ~130   n=409  인기도 1.023  (+2.3%)
     *   합계 130~160 n=372  인기도 0.995  (-0.5%)
     *   합계 160~190 n=213  인기도 0.970  (-3.0%)
     *   합계 190~200 n= 27  인기도 0.959  (-4.1%)
     *   합계 200~    n= 20  인기도 0.949  (-5.0%, 표준오차 ±0.6%p)
     * </pre>
     * 꺾이거나 뒤집히지 않고 이어지므로 후보 수를 줄이거나 합계 상한을 둘 이유가 없다. 상한 189(실제 분포 95%)를
     * 두면 이득만 1%p 줄고 얻는 것은 번호가 덜 치우쳐 보이는 것뿐이라 넣지 않았다.
     */
    private static final int CANDIDATE_POOL = 200;
    private static final String TARGET = "popularity";

    /**
     * 학습 결과
     *
     * @param regression   표준화된 특징 8개로 인기도 지수를 맞히는 회귀식
     * @param mean         학습 데이터의 특징별 평균 (추론 때 같은 기준으로 표준화)
     * @param deviation    학습 데이터의 특징별 표준편차
     * @param samples      학습에 쓴 회차 수
     * @param averageIndex 학습 데이터의 평균 인기도 지수 (게임의 인기도를 견줄 기준)
     */
    public record Model(LinearModel regression, double[] mean, double[] deviation, int samples, double averageIndex) {

        /**
         * 조합 1개의 인기도 지수 예측
         *
         * @param numbers 번호 6개
         */
        public double popularityOf(List<Integer> numbers) {
            double[] features = CombinationFeatures.of(numbers);
            double[] standardized = new double[features.length];
            for (int i = 0; i < features.length; i++) {
                standardized[i] = (features[i] - mean[i]) / deviation[i];
            }
            return regression.predict(standardized);
        }
    }

    /**
     * 회차별 (당첨 조합의 생김새 → 그 회차 인기도 지수)로 회귀 학습
     * <p>
     * 당첨 조합은 매 회차 무작위로 정해지므로, 학습 데이터는 조합 공간에서 고르게 뽑힌 표본이다.
     * 판매금액이나 5등 당첨자 수가 없는 회차(동기화 전 회차)는 건너뛰고, 남은 회차 중
     * 최근 {@value #TRAINING_WINDOW}회만 쓴다.
     *
     * @param histories 회차 오름차순 전체 이력
     * @throws InvalidLottoDataException 인기도를 계산할 수 있는 회차가 {@link #MIN_DRAWS}건 미만일 때
     */
    public Model train(List<LottoHistory> histories) {
        List<LottoHistory> usable = histories.stream()
                .filter(draw -> popularityIndex(draw) != null)
                .toList();
        if (usable.size() < MIN_DRAWS) {
            throw new InvalidLottoDataException("인기도 학습에 필요한 회차가 부족합니다. 판매금액과 5등 당첨자 수가 있는 회차가 "
                    + usable.size() + "건뿐입니다 (최소 " + MIN_DRAWS + "건). /system/sync로 동기화해 주세요.");
        }
        List<LottoHistory> recent = usable.subList(Math.max(0, usable.size() - TRAINING_WINDOW), usable.size());

        double[][] x = recent.stream().map(draw -> CombinationFeatures.of(draw.getNumbers())).toArray(double[][]::new);
        double[] y = recent.stream().mapToDouble(PopularityPredictor::popularityIndex).toArray();
        double[] mean = columnMeans(x);
        double[] deviation = columnDeviations(x, mean);
        standardize(x, mean, deviation);

        log.info("인기도 모델 학습 시작 ({}개 회차, 특징 {}개)", x.length, CombinationFeatures.FEATURE_NAMES.length);
        DataFrame trainDf = DataFrame.of(x, CombinationFeatures.FEATURE_NAMES).merge(DoubleVector.of(TARGET, y));
        LinearModel regression = RidgeRegression.fit(Formula.lhs(TARGET), trainDf, LAMBDA);
        log.info("인기도 모델 학습 완료 (설명력 R²: {})", String.format("%.4f", regression.RSquared()));
        return new Model(regression, mean, deviation, x.length, average(y));
    }

    /**
     * 후보 조합 {@value #CANDIDATE_POOL}개 중 예측 인기도가 가장 낮은 조합 선택
     * (후보 수를 그렇게 정한 근거는 {@link #CANDIDATE_POOL} 참고)
     *
     * @param random 난수 생성기 (검증에서 결과를 재현할 때 시드 지정)
     * @return 오름차순 번호 6개
     */
    public List<Integer> generate(Model model, Random random) {
        List<Integer> best = null;
        double bestPopularity = Double.MAX_VALUE;
        for (int attempt = 0; attempt < CANDIDATE_POOL; attempt++) {
            List<Integer> candidate = RandomGameGenerator.draw(random);
            double popularity = model.popularityOf(candidate);
            if (popularity < bestPopularity) {
                bestPopularity = popularity;
                best = candidate;
            }
        }
        return best;
    }

    /**
     * 회차의 5등 인기도 지수 (실제 당첨자 ÷ 기대 당첨자)
     *
     * @return 판매금액이나 5등 당첨자 수가 없으면 null
     */
    public static Double popularityIndex(LottoHistory draw) {
        PrizeRank fifth = draw.getPrize(LottoRules.PRIZE_RANKS);
        Long sellAmt = draw.getTotalSellAmt();
        if (fifth.winCo() == null || sellAmt == null || sellAmt <= 0) {
            return null;
        }
        double soldGames = sellAmt / GAME_PRICE;
        double expected = soldGames * FIFTH_COMBINATIONS / LottoRules.TOTAL_COMBINATIONS;
        return expected <= 0 ? null : fifth.winCo() / expected;
    }

    private static double[] columnMeans(double[][] x) {
        double[] mean = new double[x[0].length];
        for (double[] row : x) {
            for (int i = 0; i < row.length; i++) {
                mean[i] += row[i] / x.length;
            }
        }
        return mean;
    }

    /**
     * 특징별 표준편차 (값이 하나뿐인 특징은 0으로 나누지 않도록 아주 작은 값으로 대체)
     */
    private static double[] columnDeviations(double[][] x, double[] mean) {
        double[] deviation = new double[mean.length];
        for (double[] row : x) {
            for (int i = 0; i < row.length; i++) {
                deviation[i] += (row[i] - mean[i]) * (row[i] - mean[i]) / x.length;
            }
        }
        for (int i = 0; i < deviation.length; i++) {
            deviation[i] = Math.max(Math.sqrt(deviation[i]), MIN_DEVIATION);
        }
        return deviation;
    }

    private static void standardize(double[][] x, double[] mean, double[] deviation) {
        for (double[] row : x) {
            for (int i = 0; i < row.length; i++) {
                row[i] = (row[i] - mean[i]) / deviation[i];
            }
        }
    }

    private static double average(double[] values) {
        double total = 0;
        for (double value : values) {
            total += value;
        }
        return total / values.length;
    }
}
