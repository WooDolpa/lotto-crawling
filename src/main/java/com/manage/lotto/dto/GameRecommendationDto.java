package com.manage.lotto.dto;

import java.util.List;

/**
 * @param game           게임 라벨 (A, B, C, D, E)
 * @param numbers        정렬된 추천 번호 6개
 * @param sum            번호 총합
 * @param oddEven        홀수:짝수 비율 (예: "3:3")
 * @param highLow        저번호(1~22):고번호(23~45) 비율 (예: "3:3")
 * @param hasConsecutive 2연번 포함 여부
 */
public record GameRecommendationDto(String game, List<Integer> numbers, int sum, String oddEven,
                                    String highLow, boolean hasConsecutive) {}
