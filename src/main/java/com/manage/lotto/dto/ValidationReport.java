package com.manage.lotto.dto;

import java.util.List;

/**
 * 시간순 검증 결과
 *
 * @param firstDrawNo       첫 검증 회차
 * @param lastDrawNo        마지막 검증 회차
 * @param testDraws         검증 회차 수
 * @param refitInterval     다시 학습하는 간격 (회차)
 * @param seed              A 모델 학습·B/C 번호 뽑기 시드
 * @param significanceLevel "무작위보다 나음" 판정 기준 p값 (0.05 ÷ 게임 수)
 * @param random            무작위로 골랐을 때의 이론값
 * @param games             게임별 결과 (A, B, C 순서)
 * @param draws             회차별 결과 (오래된 회차부터)
 * @param popularity        E 인기도 모델 검증 결과 (적중률과 재는 것이 달라 따로 둔다)
 */
public record ValidationReport(int firstDrawNo, int lastDrawNo, int testDraws, int refitInterval, long seed,
                               double significanceLevel, Baseline random, List<GameResult> games,
                               List<DrawResult> draws, Popularity popularity) {

    /**
     * @param averageMatches     회차당 평균 일치 개수
     * @param prizeRate          3개 이상 일치할 확률
     * @param matchProbabilities 일치 개수(0~6)별 확률
     */
    public record Baseline(double averageMatches, double prizeRate, List<Double> matchProbabilities) {}

    /**
     * @param key               게임 키 (pattern, probability, coOccurrence3, coOccurrence4)
     * @param name              화면 이름
     * @param averageMatches    회차당 평균 일치 개수
     * @param prizeRate         3개 이상 일치한 회차 비율
     * @param matchCounts       일치 개수(0~6)별 회차 수
     * @param pValue            무작위라면 이만큼 이상 맞힐 확률 (평균 일치 개수 기준)
     * @param prizePValue       무작위라면 3개 이상 일치가 이만큼 이상 나올 확률
     * @param betterThanRandom  pValue가 significanceLevel보다 작은지
     */
    public record GameResult(String key, String name, double averageMatches, double prizeRate,
                             List<Integer> matchCounts, double pValue, double prizePValue,
                             boolean betterThanRandom) {}

    /**
     * @param picks 게임별 번호와 일치 개수 (games와 같은 순서)
     */
    public record DrawResult(int drawNo, List<Integer> actual, List<Pick> picks) {}

    public record Pick(List<Integer> numbers, int matches) {}

    /**
     * E 인기도 모델 검증 결과
     * <p>
     * A·B·C는 "맞힌 개수"로 재지만 D는 "당첨금을 몇 명과 나누는가"를 노리므로 같은 표에 넣을 수 없다.
     * 대신 회차마다 직전 이력만으로 학습한 모델이 <b>그 회차 당첨 조합의 인기도</b>를 얼마나 맞혔는지 잰다.
     *
     * @param available         인기도를 잴 수 있는 회차가 충분했는지 (아니면 나머지 값은 의미 없음)
     * @param message           잴 수 없었을 때의 사유
     * @param draws             인기도를 잰 회차 수
     * @param firstDrawNo       인기도를 잰 첫 회차
     * @param lastDrawNo        인기도를 잰 마지막 회차
     * @param correlation       예측 인기도와 실제 인기도의 상관계수
     * @param slope             실제 = a + slope × 예측 의 기울기 (1보다 작으면 예측이 낙관적)
     * @param pValue            아무 관계가 없는데 우연히 이만큼 상관이 나올 확률 (단측)
     * @param lowQuartileIndex  덜 붐빌 것으로 예측한 하위 25% 회차의 실제 평균 인기도
     * @param highQuartileIndex 더 붐빌 것으로 예측한 상위 25% 회차의 실제 평균 인기도
     * @param betterThanChance  pValue가 significanceLevel보다 작은지
     */
    public record Popularity(boolean available, String message, int draws, int firstDrawNo, int lastDrawNo,
                             double correlation, double slope, double pValue, double lowQuartileIndex,
                             double highQuartileIndex, boolean betterThanChance) {

        public static Popularity unavailable(String message) {
            return new Popularity(false, message, 0, 0, 0, 0, 0, 1, 0, 0, false);
        }
    }
}
