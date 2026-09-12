package com.manage.lotto.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameRecommendationDto {
    private String game;              // 게임 라벨 (A, B, C, D, E)
    private List<Integer> numbers;    // 정렬된 당첨 추천 번호 6개
    private int sum;                  // 번호 총합
    private String oddEven;           // 홀수:짝수 비율 (예: "3:3")
    private String highLow;           // 저번호(1~22):고번호(23~45) 비율 (예: "3:3")
    private boolean hasConsecutive;   // 2연번 포함 여부
}
