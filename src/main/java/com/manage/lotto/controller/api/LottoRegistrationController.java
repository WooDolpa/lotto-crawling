package com.manage.lotto.controller.api;

import com.manage.lotto.dto.LottoHistoryCreateRequest;
import com.manage.lotto.dto.LottoHistoryResponse;
import com.manage.lotto.service.LottoHistoryRegistrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 당첨 이력 단건 등록 API
 */
@RestController
@RequestMapping("/api/lotto/history")
@RequiredArgsConstructor
public class LottoRegistrationController {

    private final LottoHistoryRegistrationService service;

    /**
     * 회차 당첨 정보 1건 등록
     * POST /api/lotto/history (JSON)
     * <p>
     * 필수: drawNo, numbers(서로 다른 1~45 6개), bonusNumber(당첨 번호와 중복 불가)
     * 선택: firstPrizeAmount, firstPrizeWinners (없으면 0으로 저장)
     * <p>
     * 신규 회차만 저장하며 기존 회차를 덮어쓰지 않는다. 저장되면 A·B 모델 재학습 확인을 요청한다.
     * 번호 등록 화면의 단일등록 탭에서 사용한다.
     * 성공 201, 입력값·JSON 형식 오류 400, 이미 등록된 회차 409.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LottoHistoryResponse register(@RequestBody LottoHistoryCreateRequest request) {
        return service.register(request);
    }
}
