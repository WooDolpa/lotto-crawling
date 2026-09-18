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
 */
public record ValidationReport(int firstDrawNo, int lastDrawNo, int testDraws, int refitInterval, long seed,
                               double significanceLevel, Baseline random, List<GameResult> games,
                               List<DrawResult> draws) {

    /**
     * @param averageMatches     회차당 평균 일치 개수
     * @param prizeRate          3개 이상 일치할 확률
     * @param matchProbabilities 일치 개수(0~6)별 확률
     */
    public record Baseline(double averageMatches, double prizeRate, List<Double> matchProbabilities) {}

    /**
     * @param key               게임 키 (pattern, probability, unpopular)
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
}
