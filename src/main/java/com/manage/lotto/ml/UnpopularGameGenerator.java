package com.manage.lotto.ml;

import com.manage.lotto.domain.LottoRules;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 무작위로 번호를 뽑고, 사람들이 많이 고를 만한 조합이면 다시 뽑는 게임 생성
 * <p>
 * 당첨 확률은 모든 조합이 같으므로, 당첨 시 1등 당첨금을 나눠 갖는 사람이 적을 만한 조합을 노린다.
 * 제외 규칙 (전체 조합의 약 32%가 해당):
 * <ul>
 *     <li>6개 모두 31 이하 (생일 번호)</li>
 *     <li>3개 이상 연속 (예: 12-13-14)</li>
 *     <li>4개 이상 같은 간격 (예: 5-10-15-20)</li>
 *     <li>용지(7열)에서 한 줄·한 칸·대각선에 4개 이상</li>
 *     <li>직전 회차 당첨 번호와 2개 이상 겹침</li>
 * </ul>
 */
@Component
public class UnpopularGameGenerator {

    private static final int MAX_ATTEMPTS = 1_000;
    private static final int BIRTHDAY_MAX = 31;
    private static final int RUN_LENGTH = 3;
    private static final int EQUAL_GAP_LENGTH = 4;
    private static final int SHEET_LINE_LENGTH = 4;
    private static final int MAX_LAST_DRAW_OVERLAP = 1;
    /** 로또 용지 한 줄의 칸 수 (lotto-sheet.js 용지 배치와 같음) */
    private static final int SHEET_COLUMNS = 7;

    private final Random random = new SecureRandom();

    /**
     * @param lastNumbers 직전 회차 당첨 번호
     * @return 제외 규칙을 모두 통과한 오름차순 번호 6개
     */
    public List<Integer> generate(Collection<Integer> lastNumbers) {
        return generate(lastNumbers, random);
    }

    /**
     * @param lastNumbers 직전 회차 당첨 번호
     * @param random      난수 생성기 (검증에서 결과를 재현할 때 시드 지정)
     * @return 제외 규칙을 모두 통과한 오름차순 번호 6개
     */
    public List<Integer> generate(Collection<Integer> lastNumbers, Random random) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            List<Integer> candidate = draw(random);
            if (!isPopular(candidate, lastNumbers)) {
                return candidate;
            }
        }
        // 통과 확률이 약 68%라 사실상 도달하지 않음
        throw new IllegalStateException("제외 규칙을 통과한 조합을 찾지 못했습니다.");
    }

    private static List<Integer> draw(Random random) {
        List<Integer> balls = IntStream.rangeClosed(LottoRules.MIN_NUMBER, LottoRules.MAX_NUMBER)
                .boxed()
                .collect(Collectors.toCollection(ArrayList::new));
        Collections.shuffle(balls, random);
        return balls.subList(0, LottoRules.NUMBERS_PER_DRAW).stream().sorted().toList();
    }

    /**
     * 사람들이 많이 고를 만한 조합인지 확인
     *
     * @param numbers 오름차순 번호 6개
     */
    static boolean isPopular(List<Integer> numbers, Collection<Integer> lastNumbers) {
        return numbers.get(numbers.size() - 1) <= BIRTHDAY_MAX
                || hasEqualGaps(numbers, RUN_LENGTH, true)
                || hasEqualGaps(numbers, EQUAL_GAP_LENGTH, false)
                || hasSheetLine(numbers)
                || numbers.stream().filter(lastNumbers::contains).count() > MAX_LAST_DRAW_OVERLAP;
    }

    /**
     * 오름차순으로 이어진 length개 번호의 간격이 모두 같은지 확인
     *
     * @param consecutive true면 간격 1(연번)만, false면 간격 2 이상만 확인
     */
    private static boolean hasEqualGaps(List<Integer> numbers, int length, boolean consecutive) {
        for (int start = 0; start + length <= numbers.size(); start++) {
            int gap = numbers.get(start + 1) - numbers.get(start);
            if (consecutive != (gap == 1)) {
                continue;
            }
            boolean equal = true;
            for (int i = start + 1; i < start + length && equal; i++) {
                equal = numbers.get(i) - numbers.get(i - 1) == gap;
            }
            if (equal) {
                return true;
            }
        }
        return false;
    }

    /**
     * 용지의 같은 줄·칸·대각선에 SHEET_LINE_LENGTH개 이상 있는지 확인
     */
    private static boolean hasSheetLine(List<Integer> numbers) {
        Map<String, Integer> counts = new HashMap<>();
        for (int number : numbers) {
            int row = (number - 1) / SHEET_COLUMNS;
            int column = (number - 1) % SHEET_COLUMNS;
            for (String line : List.of("row" + row, "column" + column, "diagonal" + (column - row), "anti" + (column + row))) {
                if (counts.merge(line, 1, Integer::sum) >= SHEET_LINE_LENGTH) {
                    return true;
                }
            }
        }
        return false;
    }
}
