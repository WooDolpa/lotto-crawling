package com.manage.lotto.ml;

import com.manage.lotto.domain.LottoRules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 1~45에서 6개를 무작위로 뽑는 유틸
 * <p>
 * 화면에 무작위 게임을 따로 두지는 않는다. E({@link PopularityPredictor})가 후보 조합을 뽑을 때 쓴다.
 * 무작위로 뽑은 뒤 그중 덜 붐빌 조합을 고르는 방식이라 출발점이 여기다.
 */
public final class RandomGameGenerator {

    private RandomGameGenerator() {
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
