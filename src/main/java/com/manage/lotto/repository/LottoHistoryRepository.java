package com.manage.lotto.repository;

import com.manage.lotto.domain.LottoHistory;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LottoHistoryRepository extends JpaRepository<LottoHistory, Integer> {

    @Query("select h.drwNo from LottoHistory h order by h.drwNo asc")
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
     * 여러 회차 한 번에 조회
     */
    List<LottoHistory> findByDrwNoIn(Collection<Integer> drwNos);

    /**
     * 최신 회차부터 내림차순으로 limit 건 조회
     */
    List<LottoHistory> findAllByOrderByDrwNoDesc(Limit limit);
}
