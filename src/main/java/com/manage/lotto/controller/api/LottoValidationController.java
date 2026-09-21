package com.manage.lotto.controller.api;

import com.manage.lotto.dto.ValidationStatus;
import com.manage.lotto.service.LottoValidationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 시간순 검증 API (A 점수 모델, B 확률 모델, C·D 동반출현 게임을 무작위와 비교)
 */
@RestController
@RequestMapping("/api/lotto/validation")
@RequiredArgsConstructor
public class LottoValidationController {

    private final LottoValidationService validationService;

    /**
     * 검증 상태·결과 조회
     * GET /api/lotto/validation
     * <p>
     * 서버 메모리에 있는 마지막 검증 1건의 상태(IDLE, RUNNING, DONE, FAILED), 진행 단계, 완료 결과와
     * 현재 이력으로 실행할 수 있는 최대 검증 회차 수(maxTestDraws = 이력 수 - 50, 최대 500)를 반환한다.
     * 서버를 재시작하면 IDLE로 돌아간다. 모델 검증 화면이 진행 중에 주기적으로 호출한다.
     */
    @GetMapping
    public ValidationStatus status() {
        return validationService.status();
    }

    /**
     * 검증 시작
     * POST /api/lotto/validation?testDraws=300
     * <p>
     * 최근 testDraws개 회차를 각각 그 이전 이력만으로 맞혀 보고, 게임별 평균 일치 개수·3개 이상 일치 비율을
     * 무작위 이론값과 비교해 p값으로 판정한다. 20회차마다 다시 학습하며 수 분 걸릴 수 있어 백그라운드로 실행하고
     * 즉시 202와 RUNNING 상태를 반환한다.
     * 회차 수가 범위 밖이거나 이력이 (50 + testDraws)건 미만·불연속이면 400, 이미 실행 중이면 409.
     *
     * @param testDraws 검증 회차 수 (20~500)
     */
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ValidationStatus start(@RequestParam(name = "testDraws", defaultValue = "300") int testDraws) {
        return validationService.start(testDraws);
    }
}
