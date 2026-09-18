package com.manage.lotto.domain;

/**
 * 등수별 당첨 정보 (엔티티에 넘길 값을 묶어 전달하는 용도, 저장은 회차 테이블의 개별 컬럼에 한다)
 * <p>
 * 아직 받지 못한 값은 0이 아니라 null로 둔다. "당첨자 0명"과 구분되지 않으면 조합 인기도 계산이 조용히 틀어진다.
 *
 * @param winCo  당첨 인원 수
 * @param winAmt 1게임당 당첨 금액
 * @param sumAmt 해당 등수 총 당첨금
 */
public record PrizeRank(Integer winCo, Long winAmt, Long sumAmt) {
}
