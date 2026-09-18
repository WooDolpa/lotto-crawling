package com.manage.lotto.controller.api;

import com.manage.lotto.dto.SyncResponse;
import com.manage.lotto.service.SystemService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 당첨 이력 데이터 적재 API (동행복권 API 수집)
 */
@RestController
@RequestMapping(path = "/system")
@RequiredArgsConstructor
public class SystemController {

    private final SystemService systemService;

    /**
     * 동행복권 API 회차 동기화
     * GET /system/sync?startNo=1&endNo=1241
     * <p>
     * 회차 범위를 한 번에 받아 없는 회차는 추가하고 이미 있는 회차는 갱신한다.
     * 신규·갱신 건수와 회차 범위를 반환한다. 현재 이 API를 호출하는 화면은 없다.
     * 최신 회차를 자동으로 찾지 않으므로 두 회차를 모두 지정해야 한다. 범위가 뒤집히면 400.
     *
     * @param startNo 시작 회차
     * @param endNo   종료 회차 (시작 회차 이상)
     */
    @GetMapping("/sync")
    public SyncResponse syncData(@RequestParam(name = "startNo") Integer startNo,
                                 @RequestParam(name = "endNo") Integer endNo) {

        return systemService.syncData(startNo, endNo);
    }
}
