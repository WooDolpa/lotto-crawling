package com.manage.lotto.ml;

import com.manage.lotto.domain.LottoHistory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * 학습에 쓴 당첨 이력의 SHA-256 지문 (회차와 당첨 번호만 반영, 당첨금·인원·보너스 번호는 제외)
 */
public final class HistoryFingerprint {

    private HistoryFingerprint() {
    }

    /**
     * @param version   모델 특징·설정 버전 (바뀌면 이력이 같아도 지문이 달라짐)
     * @param histories 회차 오름차순 당첨 이력
     */
    public static String of(String version, List<LottoHistory> histories) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(version.getBytes(StandardCharsets.UTF_8));
            for (LottoHistory history : histories) {
                digest.update((history.getDrwNo() + ":" + history.getNumbers() + "\n").getBytes(StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
