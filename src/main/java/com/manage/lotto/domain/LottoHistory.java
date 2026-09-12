package com.manage.lotto.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

/**
 * 로또 6/45 회차별 당첨 이력 및 통계 정보 엔티티
 */
@Entity
@Table(name = "lotto_history")
@Comment("로또 6/45 회차별 당첨 이력 및 통계 정보 테이블")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LottoHistory {

    /**
     * 로또 추첨 회차 (PK)
     * 예: 1, 1000, 1240
     */
    @Id
    @Column(name = "drw_no", nullable = false)
    @Comment("로또 추첨 회차 (기본키, 예: 1240)")
    private Integer drwNo;

    /**
     * 로또 추첨 일자
     * 예: 2002-12-07 (1회차), 2026-03-07
     */
    @Column(name = "drw_date")
    @Comment("로또 추첨 일자 (토요일, 예: 2026-03-07)")
    private LocalDate drwDate;

    /**
     * 1번째 당첨 번호 (1~45)
     */
    @Column(name = "drwt_no1", nullable = false)
    @Comment("1번째 당첨 번호 (1~45)")
    private Integer drwtNo1;

    /**
     * 2번째 당첨 번호 (1~45)
     */
    @Column(name = "drwt_no2", nullable = false)
    @Comment("2번째 당첨 번호 (1~45)")
    private Integer drwtNo2;

    /**
     * 3번째 당첨 번호 (1~45)
     */
    @Column(name = "drwt_no3", nullable = false)
    @Comment("3번째 당첨 번호 (1~45)")
    private Integer drwtNo3;

    /**
     * 4번째 당첨 번호 (1~45)
     */
    @Column(name = "drwt_no4", nullable = false)
    @Comment("4번째 당첨 번호 (1~45)")
    private Integer drwtNo4;

    /**
     * 5번째 당첨 번호 (1~45)
     */
    @Column(name = "drwt_no5", nullable = false)
    @Comment("5번째 당첨 번호 (1~45)")
    private Integer drwtNo5;

    /**
     * 6번째 당첨 번호 (1~45)
     */
    @Column(name = "drwt_no6", nullable = false)
    @Comment("6번째 당첨 번호 (1~45)")
    private Integer drwtNo6;

    /**
     * 2등 결정용 보너스 번호 (1~45)
     */
    @Column(name = "bnus_no", nullable = false)
    @Comment("보너스 당첨 번호 (1~45, 2등 결정용)")
    private Integer bnusNo;

    /**
     * 해당 회차 총 판매 금액 (단위: 원)
     */
    @Column(name = "tot_sellamnt")
    @Comment("해당 회차 총 판매 금액 (단위: 원)")
    private Long totSellamnt;

    /**
     * 1등 1게임당 당첨 금액 (단위: 원)
     */
    @Column(name = "first_winamnt")
    @Comment("1등 1게임당 당첨 금액 (단위: 원)")
    private Long firstWinamnt;

    /**
     * 1등 당첨 게임(인원) 수
     */
    @Column(name = "first_przwner_co")
    @Comment("1등 당첨 게임(인원) 수")
    private Integer firstPrzwnerCo;

    /**
     * 6개 당첨 번호를 오름차순 정렬된 리스트로 반환
     */
    public List<Integer> getNumbers() {
        return Arrays.asList(drwtNo1, drwtNo2, drwtNo3, drwtNo4, drwtNo5, drwtNo6)
                .stream()
                .sorted()
                .toList();
    }

    /**
     * 특정 번호가 당첨 번호에 포함되어 있는지 확인
     */
    public boolean contains(int num) {
        return drwtNo1 == num || drwtNo2 == num || drwtNo3 == num ||
                drwtNo4 == num || drwtNo5 == num || drwtNo6 == num;
    }
}
