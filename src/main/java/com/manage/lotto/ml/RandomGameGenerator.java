package com.manage.lotto.ml;

import com.manage.lotto.domain.LottoRules;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 아무 규칙 없이 1~45에서 6개를 뽑는 게임 생성 (E 무작위 기준선)
 * <p>
 * 나머지 네 게임의 비교 기준이다. 시간순 검증에서 A·B·C는 모두 무작위와 적중률 차이가 없었으므로,
 * 화면에 실제 무작위 게임을 나란히 두어 그 사실을 매주 눈으로 확인할 수 있게 한다.
 * <p>
 * {@link #draw(Random)}는 C({@link UnpopularGameGenerator})와 D({@link PopularityPredictor})도 후보를
 * 뽑는 데 쓴다. 두 모델 모두 무작위로 뽑은 뒤 거르거나 고르는 방식이라 출발점이 같다.
 */
@Component
public class RandomGameGenerator {

    private final Random random = new SecureRandom();

    /**
     * @return 오름차순 번호 6개
     */
    public List<Integer> generate() {
        return draw(random);
    }

    /**
     * 1~45를 섞어 앞 6개를 오름차순으로 반환
     *
     * @param random 난수 생성기 (검증에서 결과를 재현할 때 시드 지정)
     */
    public static List<Integer> draw(Random random) {
        List<Integer> balls = IntStream.rangeClosed(LottoRules.MIN_NUMBER, LottoRules.MAX_NUMBER)
                .boxed()
                .collect(Collectors.toCollection(ArrayList::new));
        Collections.shuffle(balls, random);
        return balls.subList(0, LottoRules.NUMBERS_PER_DRAW).stream().sorted().toList();
    }
}
