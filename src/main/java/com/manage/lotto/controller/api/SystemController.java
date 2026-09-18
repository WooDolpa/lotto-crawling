package com.manage.lotto.controller.api;

import com.manage.lotto.dto.ExcelUploadResponse;
import com.manage.lotto.dto.SyncResponse;
import com.manage.lotto.service.SystemService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 당첨 이력 데이터 적재 API (동행복권 API 수집, 엑셀 업로드)
 */
@RestController
@RequestMapping(path = "/system")
@RequiredArgsConstructor
public class SystemController {

    private final SystemService systemService;

    /**
     * 동행복권 API 최신 회차 동기화
     * POST /system/sync?limit=100
     * <p>
     * DB에 저장된 최신 회차의 다음 회차부터 순서대로 동행복권 API에서 받아 저장한다.
     * 요청이 실패하거나 아직 추첨되지 않은 회차에 도달하면 멈추고, 저장한 회차 수를 반환한다.
     * 현재 이 API를 호출하는 화면은 없다.
     *
     * @param limit 최대 수집 회차 수 (0 이하면 최신 회차까지 수집)
     */
    @PostMapping("/sync")
    public SyncResponse syncDonghaeng(@RequestParam(name = "limit", defaultValue = "0") int limit) {
        return systemService.syncFromDonghaengApi(limit);
    }

    /**
     * 엑셀 파일 일괄 적재
     * POST /system/manual/excel (multipart, 최대 10MB)
     * <p>
     * 첫 번째 시트의 B열 회차, C~H열 당첨 번호, I열 보너스, K열 당첨 인원, L열 당첨금을 읽는다.
     * 새 회차는 추가하고, 이미 있는 회차는 당첨 번호만 갱신한다.
     * 갱신할 때 엑셀의 빈 칸은 기존 값을 유지한다.
     * 신규·갱신 건수와 회차 범위를 반환한다. 번호 등록 화면의 대량등록 탭에서 사용한다.
     * 빈 파일이면 400.
     *
     * @param file .xlsx 또는 .xls 파일
     */
    @PostMapping(path = "/manual/excel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ExcelUploadResponse manualExcel(@RequestParam("file") MultipartFile file) throws IOException {
        return systemService.saveExcel(file);
    }
}
