package com.manage.lotto.dto;

import com.manage.lotto.domain.LottoHistory;
import com.manage.lotto.domain.LottoRules;
import com.manage.lotto.domain.PrizeRank;

/**
 * 회차별 1등 당첨 정보 (통계 > 1등 당첨자수 화면)
 * <p>
 * 동기화 전 회차는 추첨일·당첨자 수·당첨금·판매금액이 null이다. 0으로 바꾸지 않는다 ("당첨자 0명"과 구분해야 한다).
 *
 * @param drawNo          회차
 * @param drawDate        추첨일 (yyyyMMdd)
 * @param winners         1등 당첨자 수
 * @param amountPerWinner 1등 1게임당 당첨금 (원)
 * @param expectedWinners 판매량으로 본 기대 1등 당첨자 수 (판매 게임 수 ÷ 전체 조합 수).
 *                        실제 ÷ 기대가 1등의 인기도 지수다. 판매금액이 없거나
 *                        {@link LottoRules#FIRST_TRUSTED_DRAW}회 미만이면 null (옛 회차로 인기도를 재지 않는다)
 */
public record FirstPrizeWinnersResponse(Integer drawNo, String drawDate, Integer winners, Long amountPerWinner,
                                        Double expectedWinners) {

    public static FirstPrizeWinnersResponse from(LottoHistory history) {
        PrizeRank first = history.getPrize(1);
        return new FirstPrizeWinnersResponse(history.getDrwNo(), history.getDrawDate(), first.winCo(), first.winAmt(),
                expectedWinners(history));
    }

    private static Double expectedWinners(LottoHistory history) {
        Long sellAmt = history.getTotalSellAmt();
        if (history.getDrwNo() < LottoRules.FIRST_TRUSTED_DRAW || sellAmt == null || sellAmt <= 0) {
            return null;
        }
        return sellAmt / (double) LottoRules.GAME_PRICE / LottoRules.TOTAL_COMBINATIONS;
    }
}
