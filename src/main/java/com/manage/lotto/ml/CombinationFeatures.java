package com.manage.lotto.ml;

import com.manage.lotto.domain.LottoRules;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;

/**
 * 조합 1개의 생김새를 숫자 9개로 바꾸는 특징 추출 (인기도 모델 E 전용)
 * <p>
 * A·B가 쓰는 {@link LottoFeatureExtractor}는 "이 공이 다음 회차에 나올까"를 보지만, 여기서는
 * "이 조합을 사람이 고를까"를 본다. 당첨 확률과는 아무 상관이 없고, 당첨됐을 때 당첨금을
 * 나눠 가질 사람 수에만 관계있다.
 * <p>
 * 특징은 사람이 번호를 고를 때 실제로 쓰는 기준에서 골랐다. 생일(31 이하), 고르게 퍼뜨리기,
 * 연번, 끝자리 맞추기, 용지 위 모양, 직전 회차 번호와의 겹침 같은 것들이다. 표본이 회차 수만큼뿐이라
 * 특징을 더 늘리면 과적합한다.
 * <p>
 * 마지막 하나(`last_draw_overlap`)만 조합 바깥을 본다. 직전 회차와 2개 이상 겹치는 조합은 실제로
 * 덜 붐볐다 (406회차 실측: 5등 −2.1% t=−5.0, 3등 −5.6% t=−5.2. 겹침 개수별로도 단조롭다).
 * "이번에 나온 번호는 또 안 나온다"고 믿는 사람이 그만큼 많다는 뜻이다.
 */
public final class CombinationFeatures {

    /** 특징 이름 (순서가 곧 {@link #of} 반환 배열의 순서) */
    public static final String[] FEATURE_NAMES = {
            "sum", "odd_count", "birthday_count", "max_run",
            "gap_spread", "tens_groups", "same_last_digit", "sheet_line", "last_draw_overlap"
    };

    /** 생일로 고를 수 있는 최대 번호 */
    private static final int BIRTHDAY_MAX = 31;
    /** 십의 자리 묶음 크기 (1~10, 11~20, ... 41~45. 로또 공 색 구분과 같음) */
    private static final int TENS_GROUP_SIZE = 10;

    private CombinationFeatures() {
    }

    /**
     * @param numbers         번호 6개 (정렬 여부 무관)
     * @param previousNumbers 직전 회차 당첨 번호 6개 (겹침 특징용. 예측 시점에 이미 아는 값이다)
     * @return {@link #FEATURE_NAMES} 순서의 특징 값
     */
    public static double[] of(List<Integer> numbers, List<Integer> previousNumbers) {
        List<Integer> sorted = numbers.stream().sorted().toList();
        return new double[]{
                sum(sorted),
                countMatching(sorted, number -> number % 2 == 1),
                countMatching(sorted, number -> number <= BIRTHDAY_MAX),
                maxRun(sorted),
                gapSpread(sorted),
                distinctTensGroups(sorted),
                maxSameLastDigit(sorted),
                maxSheetLine(sorted),
                overlap(sorted, previousNumbers)
        };
    }

    /**
     * 직전 회차 당첨 번호와 겹치는 개수 (0~6)
     */
    private static double overlap(List<Integer> numbers, List<Integer> previousNumbers) {
        Set<Integer> previous = Set.copyOf(previousNumbers);
        return numbers.stream().filter(previous::contains).count();
    }

    private static double sum(List<Integer> numbers) {
        return numbers.stream().mapToInt(Integer::intValue).sum();
    }

    private static double countMatching(List<Integer> numbers, IntPredicate condition) {
        return numbers.stream().mapToInt(Integer::intValue).filter(condition).count();
    }

    /**
     * 가장 긴 연번의 길이 (예: 12-13-14가 있으면 3)
     */
    private static double maxRun(List<Integer> numbers) {
        int longest = 1;
        int current = 1;
        for (int i = 1; i < numbers.size(); i++) {
            current = numbers.get(i) - numbers.get(i - 1) == 1 ? current + 1 : 1;
            longest = Math.max(longest, current);
        }
        return longest;
    }

    /**
     * 번호 사이 간격 5개의 표준편차 (작을수록 고르게 퍼진 조합)
     */
    private static double gapSpread(List<Integer> numbers) {
        int gapCount = numbers.size() - 1;
        double mean = (numbers.get(gapCount) - numbers.get(0)) / (double) gapCount;
        double squaredError = 0;
        for (int i = 1; i < numbers.size(); i++) {
            double gap = numbers.get(i) - numbers.get(i - 1);
            squaredError += (gap - mean) * (gap - mean);
        }
        return Math.sqrt(squaredError / gapCount);
    }

    /**
     * 번호가 걸쳐 있는 십의 자리 묶음 수 (1~5. 클수록 넓게 퍼진 조합)
     */
    private static double distinctTensGroups(List<Integer> numbers) {
        return numbers.stream().map(number -> (number - 1) / TENS_GROUP_SIZE).distinct().count();
    }

    /**
     * 끝자리가 같은 번호의 최대 개수 (예: 7-17-27이면 3)
     */
    private static double maxSameLastDigit(List<Integer> numbers) {
        return maxCount(numbers, number -> List.of("digit" + number % 10));
    }

    /**
     * 용지에서 같은 줄 또는 같은 칸에 놓인 번호의 최대 개수
     */
    private static double maxSheetLine(List<Integer> numbers) {
        return maxCount(numbers, number -> List.of(
                "row" + (number - 1) / LottoRules.SHEET_COLUMNS,
                "column" + (number - 1) % LottoRules.SHEET_COLUMNS));
    }

    /**
     * 번호마다 속하는 묶음 이름을 세어 가장 많이 겹친 묶음의 개수 반환
     */
    private static double maxCount(List<Integer> numbers, IntFunction<List<String>> groupsOf) {
        Map<String, Integer> counts = new HashMap<>();
        int max = 0;
        for (int number : numbers) {
            for (String group : groupsOf.apply(number)) {
                max = Math.max(max, counts.merge(group, 1, Integer::sum));
            }
        }
        return max;
    }
}
