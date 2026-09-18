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
     * 회차 당첨 정보 생성 (1등 당첨 금액·인원이 없으면 0으로 저장)
     *
     * @param numbers 당첨 번호 6개 (전달된 순서대로 1~6번째 번호에 저장)
     */
    public static LottoHistory of(int drwNo, List<Integer> numbers, int bonusNo, Long firstWinAmt, Integer firstWinCo) {
        return LottoHistory.builder()
                .drwNo(drwNo)
                .winNo1(numbers.get(0))
                .winNo2(numbers.get(1))
                .winNo3(numbers.get(2))
                .winNo4(numbers.get(3))
                .winNo5(numbers.get(4))
                .winNo6(numbers.get(5))
                .bonusNo(bonusNo)
                .firstWinCo(firstWinCo != null ? firstWinCo : 0)
                .firstWinAmt(firstWinAmt != null ? firstWinAmt : 0L)
                .build();
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
     * 1등 당첨 게임(인원) 수
     */
    @Column(name = "first_win_co", nullable = false)
    @Comment("1등 당첨 인원 수")
    private Integer firstWinCo;

    /**
     * 1등 1게임당 당첨 금액 (단위: 원)
     */
    @Column(name = "first_win_amt", nullable = false)
    @Comment("1등 당첨 금액")
    private Long firstWinAmt;

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
     * 당첨 번호와 보너스 번호를 갱신하고, 1등 당첨 정보는 값이 있을 때만 갱신
     */
    public void updateWinningInfo(List<Integer> numbers, int bonusNo, Long firstWinAmt, Integer firstWinCo) {
        this.winNo1 = numbers.get(0);
        this.winNo2 = numbers.get(1);
        this.winNo3 = numbers.get(2);
        this.winNo4 = numbers.get(3);
        this.winNo5 = numbers.get(4);
        this.winNo6 = numbers.get(5);
        this.bonusNo = bonusNo;
        if (firstWinAmt != null) this.firstWinAmt = firstWinAmt;
        if (firstWinCo != null) this.firstWinCo = firstWinCo;
    }

    /**
     * 특정 번호가 당첨 번호에 포함되어 있는지 확인
     */
    public boolean contains(int num) {
        return winNo1 == num || winNo2 == num || winNo3 == num ||
                winNo4 == num || winNo5 == num || winNo6 == num;
    }
}
