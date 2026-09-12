package com.manage.lotto.service;

import com.manage.lotto.domain.LottoHistory;
import com.manage.lotto.dto.GameRecommendationDto;
import com.manage.lotto.dto.LottoRecommendResponse;
import com.manage.lotto.ml.LottoFeatureExtractor;
import com.manage.lotto.ml.LottoMlPredictor;
import com.manage.lotto.repository.LottoHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class LottoRecommendationService {

    private final LottoHistoryRepository lottoHistoryRepository;
    private final LottoFeatureExtractor featureExtractor;
    private final LottoMlPredictor mlPredictor;

    private static final String[] GAME_LABELS = {"A", "B", "C", "D", "E"};

    // 캐시 저장소 (기준 회차 번호, 데이터 건수, 번호별 확률)
    private volatile Integer cachedBaseDrawNo = null;
    private volatile int cachedDataSize = 0;
    private volatile Map<Integer, Double> cachedProbabilities = null;
    private final Object lock = new Object();

    /**
     * 머신러닝 예측 확률 기반 5게임 추천 생성 (캐시 적용)
     */
    public LottoRecommendResponse recommend5Games() {
        List<LottoHistory> histories = lottoHistoryRepository.findAllByOrderByDrwNoAsc();

        Integer latestDrwNo = histories.isEmpty() ? null : histories.get(histories.size() - 1).getDrwNo();
        int dataSize = histories.size();

        Map<Integer, Double> probabilities = getOrTrainProbabilities(histories, latestDrwNo, dataSize);

        List<GameRecommendationDto> games = generate5BalancedGames(probabilities);

        return LottoRecommendResponse.builder()
                .status("SUCCESS")
                .message("Smile ML 기반 5게임 로또 번호 추천 완료")
                .baseDrawNo(latestDrwNo)
                .games(games)
                .build();
    }

    /**
     * 캐시가 유효하면 재사용하고, 데이터 변경이나 초기 구동 시에만 머신러닝 학습 수행
     */
    private Map<Integer, Double> getOrTrainProbabilities(List<LottoHistory> histories, Integer latestDrwNo, int dataSize) {
        if (cachedProbabilities != null && Objects.equals(cachedBaseDrawNo, latestDrwNo) && cachedDataSize == dataSize) {
            log.info("캐시된 머신러닝 예측 확률을 재사용합니다. (기준 회차: {}회, 데이터 수: {}건)", cachedBaseDrawNo, cachedDataSize);
            return cachedProbabilities;
        }

        synchronized (lock) {
            // 이중 검사 (Double-checked locking)
            if (cachedProbabilities != null && Objects.equals(cachedBaseDrawNo, latestDrwNo) && cachedDataSize == dataSize) {
                return cachedProbabilities;
            }

            Map<Integer, Double> probabilities;
            if (histories.size() >= 25) {
                log.info("데이터 변경 감지 또는 최초 구동: 머신러닝 학습 및 확률 추론 시작 (기준 회차: {}회, 총 {}건)", latestDrwNo, histories.size());
                LottoFeatureExtractor.FeatureDataset dataset = featureExtractor.extractTrainingDataset(histories);
                double[][] inferenceX = featureExtractor.extractInferenceFeatures(histories);
                probabilities = mlPredictor.predictProbabilities(dataset, inferenceX);
            } else {
                log.warn("DB에 저장된 회차 데이터가 25회 미만({}건)입니다. 기본 확률 가중치를 적용합니다.", histories.size());
                probabilities = mlPredictor.createFallbackProbabilities();
            }

            // 캐시 갱신
            cachedBaseDrawNo = latestDrwNo;
            cachedDataSize = dataSize;
            cachedProbabilities = probabilities;

            return probabilities;
        }
    }

    /**
     * 캐시 수동 초기화
     */
    public void clearCache() {
        synchronized (lock) {
            this.cachedBaseDrawNo = null;
            this.cachedDataSize = 0;
            this.cachedProbabilities = null;
            log.info("머신러닝 예측 확률 캐시가 초기화되었습니다.");
        }
    }

    /**
     * 머신러닝 확률 기반 가중 샘플링 및 5중 밸런스 필터를 적용하여 5게임 생성
     */
    private List<GameRecommendationDto> generate5BalancedGames(Map<Integer, Double> probabilities) {
        List<GameRecommendationDto> resultGames = new ArrayList<>();
        Set<String> uniqueCombos = new HashSet<>();
        Random random = new Random();

        int maxAttempts = 500;
        int attempts = 0;

        while (resultGames.size() < 5 && attempts < maxAttempts) {
            attempts++;
            List<Integer> candidate = sample6Numbers(probabilities, random);

            // 1. 중복 조합 체크
            String comboKey = candidate.toString();
            if (uniqueCombos.contains(comboKey)) {
                continue;
            }

            // 2. 통계 밸런스 필터 검증
            if (!passesBalanceFilters(candidate)) {
                continue;
            }

            uniqueCombos.add(comboKey);
            String label = GAME_LABELS[resultGames.size()];
            resultGames.add(createGameDto(label, candidate));
        }

        // 최대 시도 횟수 초과 시, 필터 조건을 일부 완화하여 5게임을 채움
        while (resultGames.size() < 5) {
            List<Integer> candidate = sample6Numbers(probabilities, random);
            String comboKey = candidate.toString();
            if (!uniqueCombos.contains(comboKey)) {
                uniqueCombos.add(comboKey);
                String label = GAME_LABELS[resultGames.size()];
                resultGames.add(createGameDto(label, candidate));
            }
        }

        return resultGames;
    }

    /**
     * 1~45번 번호 중 가중치(확률)에 따라 비복원 추출로 6개 번호 선택
     */
    private List<Integer> sample6Numbers(Map<Integer, Double> probabilities, Random random) {
        List<Integer> availableBalls = new ArrayList<>();
        List<Double> weights = new ArrayList<>();

        for (int i = 1; i <= 45; i++) {
            availableBalls.add(i);
            weights.add(probabilities.getOrDefault(i, 0.001));
        }

        List<Integer> selected = new ArrayList<>();

        for (int step = 0; step < 6; step++) {
            double totalWeight = 0.0;
            for (Double w : weights) {
                totalWeight += w;
            }

            double r = random.nextDouble() * totalWeight;
            double cumulative = 0.0;
            int chosenIndex = 0;

            for (int i = 0; i < weights.size(); i++) {
                cumulative += weights.get(i);
                if (r <= cumulative) {
                    chosenIndex = i;
                    break;
                }
            }

            selected.add(availableBalls.get(chosenIndex));
            availableBalls.remove(chosenIndex);
            weights.remove(chosenIndex);
        }

        Collections.sort(selected);
        return selected;
    }

    /**
     * 통계 밸런스 필터:
     * 1. 총합: 100 ~ 175
     * 2. 홀:짝 비율: 2:4, 3:3, 4:2 허용 (홀수 개수 2~4개)
     * 3. 고:저 비율: 저번호(1~22) 개수 2~4개
     * 4. 3연번 이상 배제: 예) 14, 15, 16 포함 시 탈락
     */
    private boolean passesBalanceFilters(List<Integer> numbers) {
        // 총합 필터
        int sum = numbers.stream().mapToInt(Integer::intValue).sum();
        if (sum < 100 || sum > 175) {
            return false;
        }

        // 홀짝 비율 필터
        long oddCount = numbers.stream().filter(n -> n % 2 != 0).count();
        if (oddCount < 2 || oddCount > 4) {
            return false;
        }

        // 고저 비율 필터 (1~22: 저, 23~45: 고)
        long lowCount = numbers.stream().filter(n -> n <= 22).count();
        if (lowCount < 2 || lowCount > 4) {
            return false;
        }

        // 3연번 이상 배제 필터
        for (int i = 0; i <= numbers.size() - 3; i++) {
            if (numbers.get(i) + 1 == numbers.get(i + 1) && numbers.get(i + 1) + 1 == numbers.get(i + 2)) {
                return false;
            }
        }

        return true;
    }

    /**
     * 게임별 상세 분석 통계 DTO 빌드
     */
    private GameRecommendationDto createGameDto(String label, List<Integer> numbers) {
        int sum = numbers.stream().mapToInt(Integer::intValue).sum();
        long oddCount = numbers.stream().filter(n -> n % 2 != 0).count();
        long evenCount = 6 - oddCount;

        long lowCount = numbers.stream().filter(n -> n <= 22).count();
        long highCount = 6 - lowCount;

        boolean hasConsecutive = false;
        for (int i = 0; i < numbers.size() - 1; i++) {
            if (numbers.get(i) + 1 == numbers.get(i + 1)) {
                hasConsecutive = true;
                break;
            }
        }

        return GameRecommendationDto.builder()
                .game(label)
                .numbers(numbers)
                .sum(sum)
                .oddEven(oddCount + ":" + evenCount)
                .highLow(lowCount + ":" + highCount)
                .hasConsecutive(hasConsecutive)
                .build();
    }
}
