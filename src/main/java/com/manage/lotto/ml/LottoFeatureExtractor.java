package com.manage.lotto.ml;

import com.manage.lotto.domain.LottoHistory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 역대 로또 당첨 데이터로부터 1~45번 각 공의 머신러닝 피처를 추출하는 컴포넌트
 */
@Component
public class LottoFeatureExtractor {

    public record FeatureDataset(double[][] x, int[] y) {}

    /**
     * 과거 회차 데이터로부터 학습용 피처 매트릭스(X)와 라벨(Y) 생성
     *
     * @param histories 회차 오름차순(과거 -> 최근) 정렬된 로또 기록
     * @return 학습 데이터셋 (X: 피처 벡터, Y: 1 또는 0)
     */
    public FeatureDataset extractTrainingDataset(List<LottoHistory> histories) {
        if (histories == null || histories.size() < 25) {
            throw new IllegalArgumentException("머신러닝 학습을 위해 최소 25회차 이상의 데이터가 필요합니다.");
        }

        List<double[]> featureList = new ArrayList<>();
        List<Integer> labelList = new ArrayList<>();

        // 21회차부터 마지막 회차까지 슬라이딩 윈도우로 시점별 피처/타겟 구성
        for (int t = 20; t < histories.size(); t++) {
            List<LottoHistory> pastHistories = histories.subList(0, t);
            LottoHistory targetDraw = histories.get(t);
            Set<Integer> winningNumbers = new HashSet<>(targetDraw.getNumbers());

            // 1~45번 각 공에 대해 피처 추출 및 타겟(출현 여부) 부여
            for (int ball = 1; ball <= 45; ball++) {
                double[] features = extractBallFeatures(ball, pastHistories);
                featureList.add(features);
                labelList.add(winningNumbers.contains(ball) ? 1 : 0);
            }
        }

        double[][] x = featureList.toArray(new double[0][]);
        int[] y = labelList.stream().mapToInt(Integer::intValue).toArray();

        return new FeatureDataset(x, y);
    }

    /**
     * 다음 회차 예측을 위해 1~45번 공 각각의 최신 피처 벡터(45 x 6) 추출
     *
     * @param histories 전체 회차 오름차순 정렬된 로또 기록
     * @return 45개 번호의 피처 매트릭스 (인덱스 0 = 1번 공, ... 인덱스 44 = 45번 공)
     */
    public double[][] extractInferenceFeatures(List<LottoHistory> histories) {
        if (histories == null || histories.size() < 20) {
            throw new IllegalArgumentException("피처 추출을 위해 최소 20회차 이상의 데이터가 필요합니다.");
        }

        double[][] inferenceX = new double[45][6];
        for (int ball = 1; ball <= 45; ball++) {
            inferenceX[ball - 1] = extractBallFeatures(ball, histories);
        }
        return inferenceX;
    }

    /**
     * 특정 공(ball)에 대한 6개 핵심 피처 벡터 추출
     * 1. freq_last_5: 최근 5회 출현 횟수
     * 2. freq_last_10: 최근 10회 출현 횟수
     * 3. freq_last_20: 최근 20회 출현 횟수
     * 4. absence_streak: 최근 연속 미출현 회차 수
     * 5. co_occurrence_score: 직전 회차 당첨 번호들과의 역대 동반 출현 점수
     * 6. total_appearance_rate: 누적 출현율
     */
    public double[] extractBallFeatures(int ball, List<LottoHistory> pastHistories) {
        int totalDraws = pastHistories.size();
        LottoHistory latestDraw = pastHistories.get(totalDraws - 1);
        List<Integer> lastWinningNumbers = latestDraw.getNumbers();

        // 1. 최근 5회, 10회, 20회 빈도
        int freq5 = 0;
        int freq10 = 0;
        int freq20 = 0;

        for (int i = 0; i < 20 && i < totalDraws; i++) {
            LottoHistory h = pastHistories.get(totalDraws - 1 - i);
            if (h.contains(ball)) {
                if (i < 5) freq5++;
                if (i < 10) freq10++;
                freq20++;
            }
        }

        // 4. 연속 미출현 회차 수 (absence streak)
        int absenceStreak = 0;
        for (int i = totalDraws - 1; i >= 0; i--) {
            if (pastHistories.get(i).contains(ball)) {
                break;
            }
            absenceStreak++;
        }

        // 5. 직전 회차 당첨 번호들과의 역대 동반 출현 점수
        int coOccurrenceScore = 0;
        for (LottoHistory h : pastHistories) {
            if (h.contains(ball)) {
                for (int winNum : lastWinningNumbers) {
                    if (winNum != ball && h.contains(winNum)) {
                        coOccurrenceScore++;
                    }
                }
            }
        }

        // 6. 누적 출현율
        long totalAppearances = pastHistories.stream().filter(h -> h.contains(ball)).count();
        double totalAppearanceRate = totalDraws > 0 ? (double) totalAppearances / totalDraws : 0.0;

        return new double[] {
                (double) freq5,
                (double) freq10,
                (double) freq20,
                (double) absenceStreak,
                (double) coOccurrenceScore,
                totalAppearanceRate
        };
    }
}
