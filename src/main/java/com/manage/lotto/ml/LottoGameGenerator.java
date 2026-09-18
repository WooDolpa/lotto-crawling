package com.manage.lotto.ml;

import com.manage.lotto.domain.LottoRules;
import com.manage.lotto.dto.GameRecommendationDto;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * 번호별 출현 확률 기반 가중 샘플링과 통계 밸런스 필터로 추천 게임 생성
 */
@Component
public class LottoGameGenerator {

    private static final String[] GAME_LABELS = {"A", "B", "C", "D", "E"};
    private static final int MAX_FILTERED_ATTEMPTS = 500;
    private static final double DEFAULT_WEIGHT = 0.001;
    /** 저번호 상한 (1~22: 저, 23~45: 고) */
    private static final int LOW_NUMBER_MAX = 22;

    /**
     * 가중 샘플링 및 밸런스 필터를 적용하여 중복 없는 5게임 생성
     */
    public List<GameRecommendationDto> generate(Map<Integer, Double> probabilities) {
        return generate(probabilities, GAME_LABELS.length);
    }

    /**
     * 가중 샘플링 및 밸런스 필터를 적용하여 중복 없는 count개 게임 생성
     *
     * @param count 게임 수 (1~5, 라벨은 A부터)
     */
    public List<GameRecommendationDto> generate(Map<Integer, Double> probabilities, int count) {
        return generate(probabilities, count, new Random());
    }

    /**
     * 가중 샘플링 및 밸런스 필터를 적용하여 중복 없는 count개 게임 생성
     *
     * @param count  게임 수 (1~5, 라벨은 A부터)
     * @param random 난수 생성기 (검증에서 결과를 재현할 때 시드 지정)
     */
    public List<GameRecommendationDto> generate(Map<Integer, Double> probabilities, int count, Random random) {
        if (count < 1 || count > GAME_LABELS.length) {
            throw new IllegalArgumentException("게임 수는 1~" + GAME_LABELS.length + "입니다.");
        }
        List<GameRecommendationDto> resultGames = new ArrayList<>();
        Set<List<Integer>> uniqueCombos = new HashSet<>();

        int attempts = 0;
        while (resultGames.size() < count && attempts < MAX_FILTERED_ATTEMPTS) {
            attempts++;
            List<Integer> candidate = sampleNumbers(probabilities, random);
            if (uniqueCombos.contains(candidate) || !passesBalanceFilters(candidate)) {
                continue;
            }
            uniqueCombos.add(candidate);
            resultGames.add(createGame(GAME_LABELS[resultGames.size()], candidate));
        }

        // 최대 시도 횟수 초과 시, 필터 조건 없이 남은 게임을 채움
        while (resultGames.size() < count) {
            List<Integer> candidate = sampleNumbers(probabilities, random);
            if (uniqueCombos.add(candidate)) {
                resultGames.add(createGame(GAME_LABELS[resultGames.size()], candidate));
            }
        }

        return resultGames;
    }

    /**
     * 1~45번 번호 중 가중치(확률)에 따라 비복원 추출로 6개 번호 선택
     */
    private List<Integer> sampleNumbers(Map<Integer, Double> probabilities, Random random) {
        List<Integer> availableBalls = new ArrayList<>();
        List<Double> weights = new ArrayList<>();

        for (int i = LottoRules.MIN_NUMBER; i <= LottoRules.MAX_NUMBER; i++) {
            availableBalls.add(i);
            weights.add(probabilities.getOrDefault(i, DEFAULT_WEIGHT));
        }

        List<Integer> selected = new ArrayList<>();

        for (int step = 0; step < LottoRules.NUMBERS_PER_DRAW; step++) {
            double totalWeight = 0.0;
            for (Double w : weights) {
                totalWeight += w;
            }

            double r = random.nextDouble() * totalWeight;
            double cumulative = 0.0;
            int chosenIndex = 0;

            for (int i = 0; i < weights.size(); i++) {
                cumulative += weights.get(i);
                if (r <= cumulative) {
                    chosenIndex = i;
                    break;
                }
            }

            selected.add(availableBalls.remove(chosenIndex));
            weights.remove(chosenIndex);
        }

        Collections.sort(selected);
        return selected;
    }

    /**
     * 통계 밸런스 필터:
     * 1. 총합: 100 ~ 175
     * 2. 홀:짝 비율: 2:4, 3:3, 4:2 허용 (홀수 개수 2~4개)
     * 3. 고:저 비율: 저번호(1~22) 개수 2~4개
     * 4. 3연번 이상 배제: 예) 14, 15, 16 포함 시 탈락
     */
    private boolean passesBalanceFilters(List<Integer> numbers) {
        int sum = sum(numbers);
        if (sum < 100 || sum > 175) {
            return false;
        }

        long oddCount = oddCount(numbers);
        if (oddCount < 2 || oddCount > 4) {
            return false;
        }

        long lowCount = lowCount(numbers);
        if (lowCount < 2 || lowCount > 4) {
            return false;
        }

        for (int i = 0; i <= numbers.size() - 3; i++) {
            if (numbers.get(i) + 1 == numbers.get(i + 1) && numbers.get(i + 1) + 1 == numbers.get(i + 2)) {
                return false;
            }
        }

        return true;
    }

    private GameRecommendationDto createGame(String label, List<Integer> numbers) {
        long oddCount = oddCount(numbers);
        long lowCount = lowCount(numbers);

        boolean hasConsecutive = false;
        for (int i = 0; i < numbers.size() - 1; i++) {
            if (numbers.get(i) + 1 == numbers.get(i + 1)) {
                hasConsecutive = true;
                break;
            }
        }

        return new GameRecommendationDto(
                label,
                numbers,
                sum(numbers),
                oddCount + ":" + (LottoRules.NUMBERS_PER_DRAW - oddCount),
                lowCount + ":" + (LottoRules.NUMBERS_PER_DRAW - lowCount),
                hasConsecutive
        );
    }

    private static int sum(List<Integer> numbers) {
        return numbers.stream().mapToInt(Integer::intValue).sum();
    }

    private static long oddCount(List<Integer> numbers) {
        return numbers.stream().filter(n -> n % 2 != 0).count();
    }

    private static long lowCount(List<Integer> numbers) {
        return numbers.stream().filter(n -> n <= LOW_NUMBER_MAX).count();
    }
}
