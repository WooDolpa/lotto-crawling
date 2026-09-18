package com.manage.lotto.ml;

import com.manage.lotto.domain.LottoHistory;
import com.manage.lotto.domain.LottoRules;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Function;

/**
 * 학습에 쓴 당첨 이력의 SHA-256 지문 (모델이 실제로 읽는 값만 반영)
 */
public final class HistoryFingerprint {

    private HistoryFingerprint() {
    }

    /**
     * 번호 예측 모델(A·B)용 지문 (회차와 당첨 번호만 반영, 당첨금·인원·보너스 번호는 제외)
     *
     * @param version   모델 특징·설정 버전 (바뀌면 이력이 같아도 지문이 달라짐)
     * @param histories 회차 오름차순 당첨 이력
     */
    public static String of(String version, List<LottoHistory> histories) {
        return hash(version, histories, history -> history.getDrwNo() + ":" + history.getNumbers());
    }

    /**
     * 인기도 모델(D)용 지문 (당첨 번호에 더해 인기도 계산에 쓰는 총 판매금액과 5등 당첨자 수까지 반영)
     * <p>
     * 동기화로 판매금액·당첨자 수만 채워지는 경우 당첨 번호는 그대로라서, {@link #of}를 쓰면
     * 지문이 같아 다시 학습하지 않는다.
     *
     * @param version   모델 특징·설정 버전 (바뀌면 이력이 같아도 지문이 달라짐)
     * @param histories 회차 오름차순 당첨 이력
     */
    public static String ofPopularity(String version, List<LottoHistory> histories) {
        return hash(version, histories, history -> history.getDrwNo() + ":" + history.getNumbers()
                + ":" + history.getTotalSellAmt()
                + ":" + history.getPrize(LottoRules.PRIZE_RANKS).winCo());
    }

    private static String hash(String version, List<LottoHistory> histories, Function<LottoHistory, String> line) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(version.getBytes(StandardCharsets.UTF_8));
            for (LottoHistory history : histories) {
                digest.update((line.apply(history) + "\n").getBytes(StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
