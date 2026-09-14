package com.manage.lotto;

import com.manage.lotto.domain.LottoHistory;
import com.manage.lotto.ml.*;
import com.manage.lotto.repository.LottoHistoryRepository;
import com.manage.lotto.service.LottoPatternExperimentService;
import org.springframework.web.server.ResponseStatusException;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.stream.*;

/** Executable integration checks without DB or additional test dependencies. */
public class PatternExperimentCheck {
    static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
    }
    static LottoHistory draw(int drawNo, List<Integer> n) {
        return LottoHistory.builder().drwNo(drawNo).drwtNo1(n.get(0)).drwtNo2(n.get(1)).drwtNo3(n.get(2))
                .drwtNo4(n.get(3)).drwtNo5(n.get(4)).drwtNo6(n.get(5)).bnusNo(1).build();
    }
    public static void main(String[] args) {
        List<LottoHistory> histories = new ArrayList<>(); Random random = new Random(123);
        for (int t = 1; t <= 51; t++) {
            List<Integer> balls = IntStream.rangeClosed(1, 45).boxed().collect(Collectors.toCollection(ArrayList::new));
            Collections.shuffle(balls, random); histories.add(draw(t, balls.subList(0, 6)));
        }
        LottoFeatureExtractor base = new LottoFeatureExtractor(); LottoPatternFeatures features = new LottoPatternFeatures();
        List<Integer> straight = List.of(1,2,3,4,5,6);
        check(Math.abs(LottoPatternFeatures.shape(straight)[4] - 140) < 1e-9, "straight path length");
        check(LottoPatternFeatures.shape(straight)[5] == 0, "straight path turns");
        check(LottoPatternFeatures.similarity(straight, straight) == 1, "identical pattern similarity");
        double[] before = features.features(4, histories.subList(0, 30), base, true);
        LottoHistory original = histories.get(30);
        histories.set(30, draw(31, straight));
        check(Arrays.equals(before, features.features(4, histories.subList(0, 30), base, true)), "future target cannot affect input");
        histories.set(30, original);
        LottoHistoryRepository repository = (LottoHistoryRepository) Proxy.newProxyInstance(
                LottoHistoryRepository.class.getClassLoader(), new Class[]{LottoHistoryRepository.class},
                (proxy, method, arguments) -> { if (method.getName().equals("findTop300ByOrderByDrwNoDesc")) return histories.stream().sorted(Comparator.comparing(LottoHistory::getDrwNo).reversed()).toList(); throw new UnsupportedOperationException(); });
        LottoPatternExperimentService service = new LottoPatternExperimentService(repository, base, features);
        var first = service.run(1, 42); var second = service.run(1, 42);
        check(first.equals(second), "seed reproducibility");
        check(first.nextDrawNo() == 52 && first.trials().get(0).drawNo() == 51, "chronological boundaries");
        check(first.numbers().size() == 6 && new HashSet<>(first.numbers()).size() == 6, "six unique candidates");
        check(first.numbers().stream().allMatch(n -> n >= 1 && n <= 45), "candidate range");
        check(first.pattern().matches() >= 0 && first.pattern().matches() <= 6, "match metric range");
        check(first.pattern().similarity() >= 0 && first.pattern().similarity() <= 1, "similarity range");
        check(Math.abs(first.random().matches() - .8) < .15, "uniform random baseline near theoretical mean");
        try { service.run(21,42); throw new AssertionError("invalid test size accepted"); } catch (ResponseStatusException expected) { }
        histories.remove(10);
        try { service.run(1,42); throw new AssertionError("insufficient data accepted"); } catch (ResponseStatusException expected) { }
        histories.add(draw(60, straight));
        try { service.run(1,42); throw new AssertionError("missing draw accepted"); } catch (ResponseStatusException expected) { }
        System.out.println("PASS: geometry, future isolation, deterministic model training, chronological evaluation, valid candidates, metrics, random baseline, data validation");
    }
}
