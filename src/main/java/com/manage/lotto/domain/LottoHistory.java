package com.manage.lotto.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

/**
 * 로또 회차별 당첨 정보 엔티티
 * <p>
 * 당첨 번호·보너스 번호를 뺀 나머지(추첨일, 등수별 당첨 정보, 총 판매금액)는 동행복권 API 동기화로 채우며,
 * 아직 받지 못했으면 null이다. 총 판매금액과 등수별 당첨 인원 수는 조합의 인기도를 계산하는 재료다.
 */
@Entity
@Table(name = "lotto_history")
@Comment("로또 회차별 당첨 정보")
@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LottoHistory {

    /**
     * 회차 전체 당첨 정보로 생성 (동행복권 API 동기화용)
     *
     * @param numbers  당첨 번호 6개 (전달된 순서대로 1~6번째 번호에 저장)
     * @param drawDate 추첨일 (yyyyMMdd)
     * @param prizes   1~5등 순서의 등수별 당첨 정보
     */
    public static LottoHistory of(int drwNo, List<Integer> numbers, int bonusNo, String drawDate,
                                  List<PrizeRank> prizes, Integer totalWinCo, Long totalSellAmt) {
        requirePrizeRanks(prizes);
        return LottoHistory.builder()
                .drwNo(drwNo)
                .winNo1(numbers.get(0))
                .winNo2(numbers.get(1))
                .winNo3(numbers.get(2))
                .winNo4(numbers.get(3))
                .winNo5(numbers.get(4))
                .winNo6(numbers.get(5))
                .bonusNo(bonusNo)
                .drawDate(drawDate)
                .firstWinCo(prizes.get(0).winCo()).firstWinAmt(prizes.get(0).winAmt()).firstSumAmt(prizes.get(0).sumAmt())
                .secondWinCo(prizes.get(1).winCo()).secondWinAmt(prizes.get(1).winAmt()).secondSumAmt(prizes.get(1).sumAmt())
                .thirdWinCo(prizes.get(2).winCo()).thirdWinAmt(prizes.get(2).winAmt()).thirdSumAmt(prizes.get(2).sumAmt())
                .fourthWinCo(prizes.get(3).winCo()).fourthWinAmt(prizes.get(3).winAmt()).fourthSumAmt(prizes.get(3).sumAmt())
                .fifthWinCo(prizes.get(4).winCo()).fifthWinAmt(prizes.get(4).winAmt()).fifthSumAmt(prizes.get(4).sumAmt())
                .totalWinCo(totalWinCo)
                .totalSellAmt(totalSellAmt)
                .build();
    }

    private static void requirePrizeRanks(List<PrizeRank> prizes) {
        if (prizes.size() != LottoRules.PRIZE_RANKS) {
            throw new IllegalArgumentException("등수별 당첨 정보는 1~" + LottoRules.PRIZE_RANKS + "등 순서로 "
                    + LottoRules.PRIZE_RANKS + "개여야 합니다.");
        }
    }

    /**
     * 로또 회차별 아이디 (PK, 자동 증가)
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    @Comment("로또 회차별 아이디")
    private Integer id;

    /**
     * 로또 추첨 회차 (유니크)
     * 예: 1, 1000, 1240
     */
    @Column(name = "drw_no", nullable = false, unique = true)
    @Comment("로또 추첨 회차")
    private Integer drwNo;

    @Column(name = "win_no1", nullable = false)
    @Comment("1번째 당첨번호 (1 ~ 45)")
    private Integer winNo1;

    @Column(name = "win_no2", nullable = false)
    @Comment("2번째 당첨번호 (1 ~ 45)")
    private Integer winNo2;

    @Column(name = "win_no3", nullable = false)
    @Comment("3번째 당첨번호 (1 ~ 45)")
    private Integer winNo3;

    @Column(name = "win_no4", nullable = false)
    @Comment("4번째 당첨번호 (1 ~ 45)")
    private Integer winNo4;

    @Column(name = "win_no5", nullable = false)
    @Comment("5번째 당첨번호 (1 ~ 45)")
    private Integer winNo5;

    @Column(name = "win_no6", nullable = false)
    @Comment("6번째 당첨번호 (1 ~ 45)")
    private Integer winNo6;

    /**
     * 2등 결정용 보너스 번호 (1~45)
     */
    @Column(name = "bonus_no", nullable = false)
    @Comment("보너스 당첨 번호 (1 ~ 45)")
    private Integer bonusNo;

    /**
     * 추첨일 (yyyyMMdd, 동행복권 API 응답 형식 그대로)
     */
    @Column(name = "draw_date", length = 8)
    @Comment("추첨일자")
    private String drawDate;

    @Column(name = "win_no1_co")
    @Comment("1등 당첨 인원 수")
    private Integer firstWinCo;

    /**
     * 1등 1게임당 당첨 금액 (단위: 원)
     */
    @Column(name = "win_no1_amt")
    @Comment("1등 당첨 금액")
    private Long firstWinAmt;

    @Column(name = "win_no1_sum_amt")
    @Comment("1등 총 당첨금")
    private Long firstSumAmt;

    @Column(name = "win_no2_co")
    @Comment("2등 당첨 인원 수")
    private Integer secondWinCo;

    @Column(name = "win_no2_amt")
    @Comment("2등 당첨 금액")
    private Long secondWinAmt;

    @Column(name = "win_no2_sum_amt")
    @Comment("2등 총 당첨금")
    private Long secondSumAmt;

    @Column(name = "win_no3_co")
    @Comment("3등 당첨 인원 수")
    private Integer thirdWinCo;

    @Column(name = "win_no3_amt")
    @Comment("3등 당첨 금액")
    private Long thirdWinAmt;

    @Column(name = "win_no3_sum_amt")
    @Comment("3등 총 당첨금")
    private Long thirdSumAmt;

    @Column(name = "win_no4_co")
    @Comment("4등 당첨 인원 수")
    private Integer fourthWinCo;

    @Column(name = "win_no4_amt")
    @Comment("4등 당첨 금액")
    private Long fourthWinAmt;

    @Column(name = "win_no4_sum_amt")
    @Comment("4등 총 당첨금")
    private Long fourthSumAmt;

    @Column(name = "win_no5_co")
    @Comment("5등 당첨 인원 수")
    private Integer fifthWinCo;

    @Column(name = "win_no5_amt")
    @Comment("5등 당첨 금액")
    private Long fifthWinAmt;

    @Column(name = "win_no5_sum_amt")
    @Comment("5등 총 당첨금")
    private Long fifthSumAmt;

    /**
     * 1~5등 전체 당첨 게임(인원) 수
     */
    @Column(name = "total_win_co")
    @Comment("전체 당첨자 수")
    private Integer totalWinCo;

    /**
     * 회차 총 판매금액 (단위: 원). 1,000원으로 나누면 그 회차에 팔린 게임 수가 된다.
     */
    @Column(name = "total_sell_amt")
    @Comment("총 판매금액")
    private Long totalSellAmt;

    /**
     * 등록일 (최초 저장 시 자동 입력, 갱신 시 유지)
     */
    @CreationTimestamp
    @Column(name = "created_date", nullable = false, updatable = false)
    @Comment("등록일")
    private LocalDateTime createdDate;

    /**
     * 6개 당첨 번호를 오름차순 정렬된 리스트로 반환
     */
    public List<Integer> getNumbers() {
        return Stream.of(winNo1, winNo2, winNo3, winNo4, winNo5, winNo6)
                .sorted()
                .toList();
    }

    /**
     * 등수별 당첨 정보 조회
     *
     * @param rank 1 ~ {@link LottoRules#PRIZE_RANKS}
     */
    public PrizeRank getPrize(int rank) {
        return switch (rank) {
            case 1 -> new PrizeRank(firstWinCo, firstWinAmt, firstSumAmt);
            case 2 -> new PrizeRank(secondWinCo, secondWinAmt, secondSumAmt);
            case 3 -> new PrizeRank(thirdWinCo, thirdWinAmt, thirdSumAmt);
            case 4 -> new PrizeRank(fourthWinCo, fourthWinAmt, fourthSumAmt);
            case 5 -> new PrizeRank(fifthWinCo, fifthWinAmt, fifthSumAmt);
            default -> throw new IllegalArgumentException("등수는 1~" + LottoRules.PRIZE_RANKS + "입니다.");
        };
    }

    /**
     * 당첨 번호와 보너스 번호를 갱신하고, 나머지는 전달된 값이 있을 때만 갱신 (빈 값은 기존 값 유지)
     *
     * @param prizes 1~5등 순서의 등수별 당첨 정보
     */
    public void updateWinningInfo(List<Integer> numbers, int bonusNo, String drawDate,
                                  List<PrizeRank> prizes, Integer totalWinCo, Long totalSellAmt) {
        requirePrizeRanks(prizes);
        this.winNo1 = numbers.get(0);
        this.winNo2 = numbers.get(1);
        this.winNo3 = numbers.get(2);
        this.winNo4 = numbers.get(3);
        this.winNo5 = numbers.get(4);
        this.winNo6 = numbers.get(5);
        this.bonusNo = bonusNo;
        if (drawDate != null) this.drawDate = drawDate;
        if (totalWinCo != null) this.totalWinCo = totalWinCo;
        if (totalSellAmt != null) this.totalSellAmt = totalSellAmt;
        updateFirst(prizes.get(0));
        updateSecond(prizes.get(1));
        updateThird(prizes.get(2));
        updateFourth(prizes.get(3));
        updateFifth(prizes.get(4));
    }

    private void updateFirst(PrizeRank prize) {
        if (prize.winCo() != null) this.firstWinCo = prize.winCo();
        if (prize.winAmt() != null) this.firstWinAmt = prize.winAmt();
        if (prize.sumAmt() != null) this.firstSumAmt = prize.sumAmt();
    }

    private void updateSecond(PrizeRank prize) {
        if (prize.winCo() != null) this.secondWinCo = prize.winCo();
        if (prize.winAmt() != null) this.secondWinAmt = prize.winAmt();
        if (prize.sumAmt() != null) this.secondSumAmt = prize.sumAmt();
    }

    private void updateThird(PrizeRank prize) {
        if (prize.winCo() != null) this.thirdWinCo = prize.winCo();
        if (prize.winAmt() != null) this.thirdWinAmt = prize.winAmt();
        if (prize.sumAmt() != null) this.thirdSumAmt = prize.sumAmt();
    }

    private void updateFourth(PrizeRank prize) {
        if (prize.winCo() != null) this.fourthWinCo = prize.winCo();
        if (prize.winAmt() != null) this.fourthWinAmt = prize.winAmt();
        if (prize.sumAmt() != null) this.fourthSumAmt = prize.sumAmt();
    }

    private void updateFifth(PrizeRank prize) {
        if (prize.winCo() != null) this.fifthWinCo = prize.winCo();
        if (prize.winAmt() != null) this.fifthWinAmt = prize.winAmt();
        if (prize.sumAmt() != null) this.fifthSumAmt = prize.sumAmt();
    }

    /**
     * 특정 번호가 당첨 번호에 포함되어 있는지 확인
     */
    public boolean contains(int num) {
        return winNo1 == num || winNo2 == num || winNo3 == num ||
                winNo4 == num || winNo5 == num || winNo6 == num;
    }
}
