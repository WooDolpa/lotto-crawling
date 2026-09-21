package com.manage.lotto.ml;

import com.manage.lotto.domain.LottoHistory;
import com.manage.lotto.domain.LottoRules;
import com.manage.lotto.exception.InvalidLottoDataException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 당첨 번호에 가장 자주 함께 나온 조합으로 게임을 만드는 생성기 (C 3개 조합, D 4개 조합)
 * <p>
 * 동행복권 통계 화면의 "동반 출현 번호 통계"와 같은 계산이다. 회차마다 당첨 번호 6개에서
 * {@code fixedSize}개 부분집합을 모두 꺼내 세고(보너스 번호는 쓰지 않는다), 가장 많이 나온 조합을
 * 고른 뒤 남은 자리를 무작위로 채운다.
 * <p>
 * <b>이 게임은 당첨 확률을 올리지 않는다.</b> 407회차(836~1242) 실측에서 조합별 출현 횟수 분포는
 * 공정한 추첨의 기대값과 거의 완전히 일치했다 (3개 조합이 5회 나온 것 5개, 우연 기대 4.1개 /
 * 4개 조합이 3회 나온 것 1개, 우연 기대 1.6개). 어떤 조합이 많이 나온 것은 조합이 14,190개나 되어
 * 누군가는 그만큼 나와야 하기 때문이지 그 조합이 특별해서가 아니다.
 * <p>
 * 오히려 <b>사람이 많이 고르는 쪽</b>이다. 통계 사이트가 "많이 나온 조합"을 알려주니 실제로 그쪽을
 * 사기 때문이다 (406회차: 동반출현 점수 상위 25% 회차가 하위 25%보다 약 2% 더 붐빔, r=0.155 t=3.2).
 * 덜 붐비는 쪽을 노리는 E({@link PopularityPredictor})와 반대 방향에 두어 대비를 보여 주는 것이
 * 이 게임의 쓰임새다.
 * <p>
 * 집계는 이력이 있는 회차 전부로 한다. 당첨 번호만 쓰는 계산이라 운영 기관과 무관하므로
 * {@link LottoRules#FIRST_TRUSTED_DRAW}를 적용하지 않는다. 407회차 기준 1.4만 번 세면 끝나서
 * 요청마다 다시 세며, 집계 테이블이나 캐시를 두지 않는다.
 */
@Component
public class CoOccurrenceGameGenerator {

    /** 번호가 작은 조합이 앞에 오도록 (같은 이력·같은 시드면 항상 같은 조합을 고르게 한다) */
    private static final Comparator<List<Integer>> COMBINATION_ORDER = (left, right) -> {
        for (int i = 0; i < left.size(); i++) {
            int compared = Integer.compare(left.get(i), right.get(i));
            if (compared != 0) {
                return compared;
            }
        }
        return 0;
    };

    /**
     * 최다 동반출현 조합 1개에 무작위 번호를 채워 1게임 생성
     *
     * @param fixedSize 동반출현으로 고정할 번호 수 (C는 3, D는 4)
     * @param histories 회차 오름차순 이력 (검증에서는 그 회차보다 이전 이력만 넘긴다)
     * @param random    난수 생성기 (검증에서 결과를 재현할 때 시드 지정)
     * @return 오름차순 번호 6개
     */
    public List<Integer> generate(int fixedSize, List<LottoHistory> histories, Random random) {
        List<List<Integer>> best = mostFrequent(fixedSize, histories);
        // 최다 조합이 여럿이면 그중 하나를 무작위로 (3개 조합은 지금도 5개가 동점이다)
        List<Integer> fixed = best.get(random.nextInt(best.size()));
        return fill(fixed, random);
    }

    /**
     * 가장 많이 함께 나온 조합 목록 (동점이면 전부, 번호 오름차순 정렬)
     *
     * @throws InvalidLottoDataException 이력이 없을 때
     */
    List<List<Integer>> mostFrequent(int size, List<LottoHistory> histories) {
        if (size < 1 || size > LottoRules.NUMBERS_PER_DRAW) {
            throw new IllegalArgumentException("고정할 번호 수는 1~" + LottoRules.NUMBERS_PER_DRAW + "개여야 합니다.");
        }
        if (histories.isEmpty()) {
            throw new InvalidLottoDataException("동반출현을 셀 당첨 이력이 없습니다.");
        }
        Map<List<Integer>, Integer> counts = new HashMap<>();
        for (LottoHistory draw : histories) {
            for (List<Integer> combination : combinations(draw.getNumbers().stream().sorted().toList(), size)) {
                counts.merge(combination, 1, Integer::sum);
            }
        }
        int max = counts.values().stream().mapToInt(Integer::intValue).max().orElseThrow();
        return counts.entrySet().stream()
                .filter(entry -> entry.getValue() == max)
                .map(Map.Entry::getKey)
                .sorted(COMBINATION_ORDER)
                .toList();
    }

    /**
     * 번호 목록에서 size개를 뽑는 모든 조합 (6개에서 3개면 20가지, 4개면 15가지)
     *
     * @param numbers 오름차순 번호
     */
    private static List<List<Integer>> combinations(List<Integer> numbers, int size) {
        List<List<Integer>> result = new ArrayList<>();
        int[] index = IntStream.range(0, size).toArray();
        while (true) {
            result.add(IntStream.of(index).mapToObj(numbers::get).toList());
            int position = size - 1;
            while (position >= 0 && index[position] == numbers.size() - size + position) {
                position--;
            }
            if (position < 0) {
                return result;
            }
            index[position]++;
            for (int next = position + 1; next < size; next++) {
                index[next] = index[next - 1] + 1;
            }
        }
    }

    /**
     * 고정 번호에 남은 자리를 무작위로 채워 오름차순 6개로 반환
     */
    private static List<Integer> fill(List<Integer> fixed, Random random) {
        List<Integer> pool = IntStream.rangeClosed(LottoRules.MIN_NUMBER, LottoRules.MAX_NUMBER)
                .boxed()
                .filter(number -> !fixed.contains(number))
                .collect(Collectors.toCollection(ArrayList::new));
        Collections.shuffle(pool, random);
        List<Integer> numbers = new ArrayList<>(fixed);
        numbers.addAll(pool.subList(0, LottoRules.NUMBERS_PER_DRAW - fixed.size()));
        return numbers.stream().sorted().toList();
    }
}
