package com.manage.lotto.repository;

import com.manage.lotto.domain.LottoHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LottoHistoryRepository extends JpaRepository<LottoHistory, Integer> {
    @org.springframework.data.jpa.repository.Query("select h.drwNo from LottoHistory h order by h.drwNo asc")
    List<Integer> findDrawNumbers();

    List<LottoHistory> findByDrwNoBetweenOrderByDrwNoAsc(Integer fromDrawNo, Integer toDrawNo);

    /**
     * 가장 최신 회차 조회
     */
    Optional<LottoHistory> findTopByOrderByDrwNoDesc();

    /**
     * 전체 회차 오름차순 정렬 조회
     */
    List<LottoHistory> findAllByOrderByDrwNoAsc();

    /**
     * 특정 회차 존재 여부
     */
    boolean existsByDrwNo(Integer drwNo);

    /**
     * 최신 N개 회차 내림차순 조회
     */
    List<LottoHistory> findTop300ByOrderByDrwNoDesc();
}
