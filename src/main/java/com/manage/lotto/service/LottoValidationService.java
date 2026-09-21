package com.manage.lotto.service;

import com.manage.lotto.domain.LottoHistory;
import com.manage.lotto.domain.LottoRules;
import com.manage.lotto.dto.ValidationReport;
import com.manage.lotto.dto.ValidationReport.Baseline;
import com.manage.lotto.dto.ValidationReport.DrawResult;
import com.manage.lotto.dto.ValidationReport.GameResult;
import com.manage.lotto.dto.ValidationReport.Pick;
import com.manage.lotto.dto.ValidationReport.Popularity;
import com.manage.lotto.dto.ValidationStatus;
import com.manage.lotto.exception.InvalidLottoDataException;
import com.manage.lotto.exception.ValidationInProgressException;
import com.manage.lotto.ml.CoOccurrenceGameGenerator;
import com.manage.lotto.ml.LottoFeatureExtractor;
import com.manage.lotto.ml.LottoGameGenerator;
import com.manage.lotto.ml.LottoMlPredictor;
import com.manage.lotto.ml.LottoPatternTrainer;
import com.manage.lotto.ml.PopularityPredictor;
import com.manage.lotto.ml.PopularityStatistics;
import com.manage.lotto.ml.RandomMatchStatistics;
import com.manage.lotto.repository.LottoHistoryRepository;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import smile.classification.RandomForest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

/**
 * 시간순 검증: 과거 회차를 하나씩 "모르는 척" 맞혀 보고, 무작위로 고른 것보다 나은지 통계로 판정
 * <p>
 * 검증 회차마다 그 회차보다 이전 이력만 사용한다. 학습은 REFIT_INTERVAL 회차마다 다시 하고,
 * 구간 안에서는 같은 모델에 최신 특징을 넣는다 (운영 모델이 재학습 전 쓰는 방식과 같음).
 * 수 분 걸릴 수 있어 백그라운드 스레드 하나에서 한 번에 한 건만 실행한다.
 * <p>
 * 두 가지를 따로 잰다. A·B·C·D는 <b>맞힌 개수</b>를 무작위 이론값과 비교하고, E는 노리는 것이 적중률이
 * 아니므로 <b>인기도 예측이 실제와 맞았는지</b>를 따로 잰다 ({@link PopularityStatistics}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LottoValidationService {

    public static final int MIN_TEST_DRAWS = 20;
    public static final int MAX_TEST_DRAWS = 500;
    /** 다시 학습하는 간격 (회차) */
    public static final int REFIT_INTERVAL = 20;
    private static final long SEED = 42;
    /** 여러 게임을 함께 판정하므로 게임 수로 나눠 기준을 엄격하게 둠 */
    private static final double SIGNIFICANCE = 0.05;

    /** 번호 예측 API 응답과 같은 게임 키 */
    private static final List<String> GAME_KEYS = List.of("pattern", "probability", "coOccurrence3", "coOccurrence4");
    private static final List<String> GAME_NAMES = List.of("A · 점수 모델", "B · 확률 모델",
            "C · 동반출현 3개", "D · 동반출현 4개");
    /** C·D가 동반출현으로 고정하는 번호 수 */
    private static final int TRIPLE = 3;
    private static final int QUAD = 4;

    private final LottoHistoryRepository repository;
    private final LottoPatternTrainer patternTrainer;
    private final LottoMlPredictor probabilityPredictor;
    private final LottoGameGenerator gameGenerator;
    private final CoOccurrenceGameGenerator coOccurrenceGenerator;
    private final PopularityPredictor popularityPredictor;

    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "model-validation");
        thread.setDaemon(true);
        return thread;
    });
    private volatile ValidationStatus status = ValidationStatus.idle();

    /**
     * 현재 작업 상태 + 지금 이력으로 실행할 수 있는 최대 검증 회차 수
     */
    public ValidationStatus status() {
        return status.withMaxTestDraws(maxTestDraws(repository.count()));
    }

    /**
     * 검증 회차마다 이전 이력이 최소 MIN_HISTORY건 필요하므로 (이력 수 - MIN_HISTORY), 최대 MAX_TEST_DRAWS
     */
    private static int maxTestDraws(long historyCount) {
        long max = Math.min(MAX_TEST_DRAWS, historyCount - LottoPatternTrainer.MIN_HISTORY);
        return max < MIN_TEST_DRAWS ? 0 : (int) max;
    }

    /**
     * 검증 시작 (즉시 반환, 진행 상황은 status()로 확인)
     *
     * @param testDraws 검증 회차 수 (최근 회차부터 거슬러, 20~500)
     * @throws ValidationInProgressException 이미 실행 중일 때
     * @throws InvalidLottoDataException     회차 수가 범위 밖이거나 이력이 부족·불연속일 때
     */
    public synchronized ValidationStatus start(int testDraws) {
        if (status.state() == ValidationStatus.State.RUNNING) {
            throw new ValidationInProgressException("이미 검증이 진행 중입니다. 끝난 뒤 다시 실행해 주세요.");
        }
        if (testDraws < MIN_TEST_DRAWS || testDraws > MAX_TEST_DRAWS) {
            throw new InvalidLottoDataException("검증 회차 수는 " + MIN_TEST_DRAWS + "~" + MAX_TEST_DRAWS + "입니다.");
        }
        List<LottoHistory> histories = repository.findAllByOrderByDrwNoAsc();
        int required = LottoPatternTrainer.MIN_HISTORY + testDraws;
        if (histories.size() < required) {
            throw new InvalidLottoDataException("최소 " + required + "회차의 연속된 이력이 필요합니다. (현재 " + histories.size() + "회차)");
        }
        LottoRules.requireContinuousHistory(histories);

        int blocks = (testDraws + REFIT_INTERVAL - 1) / REFIT_INTERVAL;
        status = ValidationStatus.running(testDraws, blocks + 1);
        worker.execute(() -> run(histories, testDraws));
        return status.withMaxTestDraws(maxTestDraws(histories.size()));
    }

    private void run(List<LottoHistory> histories, int testDraws) {
        try {
            long started = System.currentTimeMillis();
            ValidationReport report = validate(histories, testDraws);
            status = status.done(report);
            log.info("시간순 검증 완료: {}회차, {}초", testDraws, (System.currentTimeMillis() - started) / 1000);
        } catch (Exception e) {
            log.error("시간순 검증 실패", e);
            status = status.failed(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    private ValidationReport validate(List<LottoHistory> histories, int testDraws) {
        int size = histories.size();
        int firstTest = size - testDraws;

        // 1단계: t번째 회차를 맞히는 특징(t 이전 이력으로 계산)을 한 번만 만들어 모든 구간·모델이 나눠 씀
        double[][][] features = new double[size][][];
        for (int t = LottoFeatureExtractor.MIN_PAST_DRAWS; t < size; t++) {
            checkCancelled();
            List<LottoHistory> past = histories.subList(0, t);
            features[t] = new double[LottoRules.MAX_NUMBER][];
            for (int ball = LottoRules.MIN_NUMBER; ball <= LottoRules.MAX_NUMBER; ball++) {
                features[t][ball - 1] = patternTrainer.features(ball, past);
            }
        }
        int completed = 1;
        status = status.progress(completed);

        // 2단계: REFIT_INTERVAL 회차마다 그 이전 이력으로 다시 학습하고, 구간 안의 회차를 맞힘
        List<DrawResult> draws = new ArrayList<>();
        PopularitySamples popularitySamples = new PopularitySamples();
        for (int blockStart = firstTest; blockStart < size; blockStart += REFIT_INTERVAL) {
            checkCancelled();
            List<double[]> rows = new ArrayList<>();
            List<Integer> labels = new ArrayList<>();
            for (int t = LottoFeatureExtractor.MIN_PAST_DRAWS; t < blockStart; t++) {
                for (int ball = LottoRules.MIN_NUMBER; ball <= LottoRules.MAX_NUMBER; ball++) {
                    rows.add(features[t][ball - 1]);
                    labels.add(histories.get(t).contains(ball) ? 1 : 0);
                }
            }
            double[][] x = rows.toArray(double[][]::new);
            int[] y = labels.stream().mapToInt(Integer::intValue).toArray();

            // A·B는 같은 기본 6개 특징을 쓰고 학습 설정과 번호 고르는 방식만 다름
            RandomForest score = patternTrainer.fit(x, y, SEED);
            RandomForest probability = probabilityPredictor.train(new LottoFeatureExtractor.FeatureDataset(x, y));
            // D는 공이 아니라 당첨 조합의 생김새를 보므로 위 특징과 무관하게 따로 학습한다
            PopularityPredictor.Model popularity = trainPopularity(histories.subList(0, blockStart));

            for (int t = blockStart; t < Math.min(size, blockStart + REFIT_INTERVAL); t++) {
                LottoHistory target = histories.get(t);
                List<Integer> actual = target.getNumbers();
                Random random = new Random(SEED + target.getDrwNo());
                // C·D도 그 회차보다 이전 이력만으로 센다
                List<LottoHistory> pastDraws = histories.subList(0, t);
                List<List<Integer>> picks = List.of(
                        patternTrainer.infer(score, features[t]),
                        gameGenerator.generate(probabilityPredictor.probabilities(probability, features[t]), 1, random).get(0).numbers(),
                        coOccurrenceGenerator.generate(TRIPLE, pastDraws, random),
                        coOccurrenceGenerator.generate(QUAD, pastDraws, random));
                draws.add(new DrawResult(target.getDrwNo(), actual,
                        picks.stream().map(numbers -> new Pick(numbers, matches(numbers, actual))).toList()));
                popularitySamples.add(popularity, target, histories.get(t - 1).getNumbers());
            }
            status = status.progress(++completed);
        }

        double level = SIGNIFICANCE / GAME_KEYS.size();
        List<GameResult> games = IntStream.range(0, GAME_KEYS.size())
                .mapToObj(game -> summarize(game, draws, level))
                .toList();
        Baseline random = new Baseline(RandomMatchStatistics.expectedMatches(), RandomMatchStatistics.prizeProbability(),
                IntStream.rangeClosed(0, LottoRules.NUMBERS_PER_DRAW).mapToObj(RandomMatchStatistics::matchProbability).toList());
        // 인기도는 적중률과 다른 가설을 한 번만 검정하므로 게임 수로 나누지 않는다
        return new ValidationReport(draws.get(0).drawNo(), draws.get(draws.size() - 1).drawNo(), testDraws, REFIT_INTERVAL,
                SEED, level, random, games, draws, popularitySamples.summarize(SIGNIFICANCE));
    }

    /**
     * 판매금액·5등 당첨자 수가 있는 회차가 부족한 구간에서는 인기도 검증만 건너뜀 (적중률 검증은 그대로 진행)
     *
     * @return 학습하지 못했으면 null
     */
    private PopularityPredictor.Model trainPopularity(List<LottoHistory> past) {
        try {
            return popularityPredictor.train(past);
        } catch (InvalidLottoDataException e) {
            log.info("인기도 검증 건너뜀 ({}회차까지): {}", past.size(), e.getMessage());
            return null;
        }
    }

    /**
     * 회차별 (직전 이력으로 학습한 모델의 예측 인기도, 실제 인기도) 모음
     * <p>
     * {@link LottoRules#FIRST_TRUSTED_DRAW}회 미만과 판매금액·5등 당첨자 수가 없는 회차는 건너뛰므로
     * 적중률 검증보다 회차 수가 적을 수 있다.
     */
    private static final class PopularitySamples {

        private final List<Double> predicted = new ArrayList<>();
        private final List<Double> actual = new ArrayList<>();
        private final List<Integer> drawNos = new ArrayList<>();

        /**
         * @param previousNumbers 검증 회차 바로 앞 회차의 당첨 번호 (겹침 특징용. 이력은 연속이므로 항상 있다)
         */
        void add(PopularityPredictor.Model model, LottoHistory target, List<Integer> previousNumbers) {
            // 옛 회차는 실제 인기도 자체를 지금과 같은 자로 잴 수 없어 채점에서도 뺀다 (적중률 검증은 그대로 진행)
            if (target.getDrwNo() < LottoRules.FIRST_TRUSTED_DRAW) {
                return;
            }
            Double index = PopularityPredictor.popularityIndex(target);
            if (model == null || index == null) {
                return;
            }
            predicted.add(model.popularityOf(target.getNumbers(), previousNumbers));
            actual.add(index);
            drawNos.add(target.getDrwNo());
        }

        Popularity summarize(double level) {
            if (predicted.size() < PopularityStatistics.MIN_DRAWS) {
                return Popularity.unavailable(LottoRules.FIRST_TRUSTED_DRAW + "회 이후로 판매금액과 5등 당첨자 수가 모두 있는 "
                        + "검증 회차가 " + predicted.size() + "회뿐입니다 (최소 " + PopularityStatistics.MIN_DRAWS
                        + "회). /system/sync로 동기화해 주세요.");
            }
            PopularityStatistics.Accuracy accuracy = PopularityStatistics.of(toArray(predicted), toArray(actual));
            return new Popularity(true, null, predicted.size(), drawNos.get(0), drawNos.get(drawNos.size() - 1),
                    accuracy.correlation(), accuracy.slope(), accuracy.pValue(), accuracy.lowQuartileIndex(),
                    accuracy.highQuartileIndex(), accuracy.pValue() < level);
        }

        private static double[] toArray(List<Double> values) {
            return values.stream().mapToDouble(Double::doubleValue).toArray();
        }
    }

    private static GameResult summarize(int game, List<DrawResult> draws, double level) {
        int[] counts = new int[LottoRules.NUMBERS_PER_DRAW + 1];
        int total = 0;
        for (DrawResult draw : draws) {
            int matches = draw.picks().get(game).matches();
            counts[matches]++;
            total += matches;
        }
        int prizeDraws = IntStream.range(RandomMatchStatistics.PRIZE_MATCHES, counts.length).map(k -> counts[k]).sum();
        double pValue = RandomMatchStatistics.totalMatchesPValue(total, draws.size());
        return new GameResult(GAME_KEYS.get(game), GAME_NAMES.get(game), (double) total / draws.size(),
                (double) prizeDraws / draws.size(), Arrays.stream(counts).boxed().toList(), pValue,
                RandomMatchStatistics.prizeDrawsPValue(prizeDraws, draws.size()), pValue < level);
    }

    private static int matches(List<Integer> numbers, List<Integer> actual) {
        return (int) numbers.stream().filter(actual::contains).count();
    }

    private static void checkCancelled() {
        if (Thread.currentThread().isInterrupted()) {
            throw new CancellationException("서버가 종료되어 검증을 중단했습니다.");
        }
    }

    @PreDestroy
    public void close() {
        worker.shutdownNow();
    }
}
