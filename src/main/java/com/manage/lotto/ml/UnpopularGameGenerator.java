package com.manage.lotto.ml;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.List;
import java.util.Random;

/**
 * 무작위로 번호를 뽑고, 사람들이 많이 고를 만한 조합이면 다시 뽑는 게임 생성 (C)
 * <p>
 * 당첨 확률은 모든 조합이 같으므로, 당첨 시 당첨금을 나눠 갖는 사람이 적을 만한 조합을 노린다.
 * 제외 규칙은 <b>하나뿐</b>이다: 6개가 모두 {@value #BIRTHDAY_MAX} 이하인 조합 (생일 번호).
 * 전체 조합의 약 9%가 해당한다.
 * <p>
 * <b>규칙이 하나만 남은 이유</b>: 흔히 말하는 제외 규칙들을 1,239회차 실측으로 확인한 결과
 * 생일 번호만 근거가 있었다 (5등 인기도 +4.1%, 3등 +10.3%).
 * <ul>
 *     <li>3개 이상 연속·4개 이상 같은 간격·용지 한 줄에 4개: 인기도 차이가 없다. 사람들이 피한다는
 *     통념과 달리 그런 조합을 특별히 많이 사지도, 적게 사지도 않는다.</li>
 *     <li>직전 회차와 2개 이상 겹침: <b>오히려 덜 붐비는 조합이라</b> 제외하면 손해였다 (5등 -2.9%).</li>
 * </ul>
 * 근거 없는 규칙을 남겨 두면 조합 공간만 좁아지고 얻는 것이 없어 모두 뺐다.
 * <p>
 * 사람이 규칙을 정하는 대신 데이터에서 배우는 쪽은 D({@link PopularityPredictor})다.
 */
@Component
public class UnpopularGameGenerator {

    /** 통과할 때까지 다시 뽑는 최대 횟수 */
    private static final int MAX_ATTEMPTS = 1_000;
    /** 생일로 고를 수 있는 최대 번호 */
    private static final int BIRTHDAY_MAX = 31;

    private final Random random = new SecureRandom();

    /**
     * @return 제외 규칙을 통과한 오름차순 번호 6개
     */
    public List<Integer> generate() {
        return generate(random);
    }

    /**
     * @param random 난수 생성기 (검증에서 결과를 재현할 때 시드 지정)
     * @return 제외 규칙을 통과한 오름차순 번호 6개
     */
    public List<Integer> generate(Random random) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            List<Integer> candidate = RandomGameGenerator.draw(random);
            if (!isPopular(candidate)) {
                return candidate;
            }
        }
        // 통과 확률이 약 91%라 사실상 도달하지 않음
        throw new IllegalStateException("제외 규칙을 통과한 조합을 찾지 못했습니다.");
    }

    /**
     * 사람들이 많이 고를 만한 조합인지 확인 (6개가 모두 생일 번호 범위인지)
     *
     * @param numbers 오름차순 번호 6개
     */
    static boolean isPopular(List<Integer> numbers) {
        return numbers.get(numbers.size() - 1) <= BIRTHDAY_MAX;
    }
}
