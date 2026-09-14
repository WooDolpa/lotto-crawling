package com.manage.lotto.service;

import com.manage.lotto.domain.LottoHistory;
import com.manage.lotto.ml.*;
import com.manage.lotto.repository.LottoHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import smile.classification.RandomForest;
import smile.base.cart.SplitRule;
import smile.data.DataFrame;
import smile.data.formula.Formula;
import smile.data.vector.IntVector;
import java.util.*;
import java.util.stream.*;

@Service
@RequiredArgsConstructor
public class LottoPatternExperimentService {
    private final LottoHistoryRepository repository;
    private final LottoFeatureExtractor base;
    private final LottoPatternFeatures patterns;
    public record Prediction(List<Integer> numbers, int matches, double similarity) {}
    public record Trial(int drawNo, List<Integer> actual, Prediction pattern, Prediction numberOnly,
                        double randomMatches, double randomSimilarity) {}
    public record Average(double matches, double similarity) {}
    public record Report(int baseDrawNo, int nextDrawNo, int trainingDraws, int testDraws, long seed,
                         List<Integer> numbers, Average pattern, Average numberOnly, Average random,
                         List<Trial> trials) {}

    // One bounded experiment at a time; no silent fallback on training failure.
    public synchronized Report run(int testDraws, long seed) {
        if (testDraws < 1 || testDraws > 20) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "검증 회차 수는 1~20입니다.");
        List<LottoHistory> histories = new ArrayList<>(repository.findTop300ByOrderByDrwNoDesc());
        histories.sort(Comparator.comparing(LottoHistory::getDrwNo));
        if (histories.size() < 50 + testDraws) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "최소 " + (50 + testDraws) + "회차의 이력이 필요합니다.");
        for (int i = 0; i < histories.size(); i++) {
            List<Integer> numbers = histories.get(i).getNumbers();
            if (new HashSet<>(numbers).size() != 6 || numbers.stream().anyMatch(n -> n < 1 || n > 45) ||
                    (i > 0 && histories.get(i).getDrwNo() != histories.get(i - 1).getDrwNo() + 1))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "연속 회차와 유효한 당첨 번호가 필요합니다.");
        }
        List<Trial> trials = new ArrayList<>();
        for (int t = histories.size() - testDraws; t < histories.size(); t++) {
            List<LottoHistory> past = histories.subList(0, t);
            List<Integer> actual = histories.get(t).getNumbers();
            List<Integer> pattern = predict(past, true, seed);
            List<Integer> number = predict(past, false, seed);
            Random random = new Random(seed + histories.get(t).getDrwNo());
            double matches = 0, similarity = 0;
            for (int repeat = 0; repeat < 1000; repeat++) {
                List<Integer> balls = IntStream.rangeClosed(1, 45).boxed().collect(Collectors.toCollection(ArrayList::new));
                Collections.shuffle(balls, random);
                Prediction p = measure(balls.subList(0, 6), actual);
                matches += p.matches(); similarity += p.similarity();
            }
            trials.add(new Trial(histories.get(t).getDrwNo(), actual, measure(pattern, actual), measure(number, actual), matches / 1000, similarity / 1000));
        }
        int latest = histories.get(histories.size() - 1).getDrwNo();
        return new Report(latest, latest + 1, histories.size(), testDraws, seed,
                predict(histories, true, seed),
                new Average(trials.stream().mapToInt(t -> t.pattern().matches()).average().orElseThrow(), trials.stream().mapToDouble(t -> t.pattern().similarity()).average().orElseThrow()),
                new Average(trials.stream().mapToInt(t -> t.numberOnly().matches()).average().orElseThrow(), trials.stream().mapToDouble(t -> t.numberOnly().similarity()).average().orElseThrow()),
                new Average(trials.stream().mapToDouble(Trial::randomMatches).average().orElseThrow(), trials.stream().mapToDouble(Trial::randomSimilarity).average().orElseThrow()), trials);
    }

    private Prediction measure(List<Integer> predicted, List<Integer> actual) {
        return new Prediction(predicted.stream().sorted().toList(), (int) predicted.stream().filter(actual::contains).count(), LottoPatternFeatures.similarity(predicted, actual));
    }

    public List<Integer> predict(List<LottoHistory> histories, boolean pattern, long seed) {
        return infer(train(histories, pattern, seed), histories, pattern);
    }

    public RandomForest train(List<LottoHistory> histories, boolean pattern, long seed) {
        List<double[]> rows = new ArrayList<>(); List<Integer> labels = new ArrayList<>();
        for (int t = 20; t < histories.size(); t++) {
            List<LottoHistory> past = histories.subList(0, t);
            for (int ball = 1; ball <= 45; ball++) {
                rows.add(patterns.features(ball, past, base, pattern));
                labels.add(histories.get(t).contains(ball) ? 1 : 0);
            }
        }
        double[][] x = rows.toArray(double[][]::new);
        String[] names = IntStream.range(0, x[0].length).mapToObj(i -> "f" + i).toArray(String[]::new);
        DataFrame train = DataFrame.of(x, names).merge(IntVector.of("target", labels.stream().mapToInt(Integer::intValue).toArray()));
        // Smile 3.1.1 only applies tree seeds greater than 1.
        RandomForest model = RandomForest.fit(Formula.lhs("target"), train, 60, 0, SplitRule.GINI, 12, 100, 5, 1.0, null, new Random(seed).longs(2, Long.MAX_VALUE).distinct().limit(60));
        return model;
    }

    public List<Integer> infer(RandomForest model, List<LottoHistory> histories, boolean pattern) {
        double[][] inference = new double[45][];
        for (int ball = 1; ball <= 45; ball++) inference[ball - 1] = patterns.features(ball, histories, base, pattern);
        String[] names = IntStream.range(0, inference[0].length).mapToObj(i -> "f" + i).toArray(String[]::new);
        DataFrame frame = DataFrame.of(inference, names);
        Map<Integer, Double> scores = new HashMap<>();
        for (int i = 0; i < 45; i++) {
            double[] posterior = new double[2]; model.predict(frame.get(i), posterior);
            if (!Double.isFinite(posterior[1])) throw new IllegalStateException("유효하지 않은 모델 점수");
            scores.put(i + 1, posterior[1]);
        }
        return scores.keySet().stream().sorted(Comparator.<Integer>comparingDouble(scores::get).reversed().thenComparingInt(Integer::intValue)).limit(6).sorted().toList();
    }
}
