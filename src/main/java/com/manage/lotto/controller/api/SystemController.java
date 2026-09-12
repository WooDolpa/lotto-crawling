package com.manage.lotto.controller.api;

import com.manage.lotto.service.SystemService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping(path = "/system")
@RequiredArgsConstructor
public class SystemController {

    private final SystemService systemService;

    /**
     * 동행복권 API 최신 회차 동기화
     * POST /system/sync?limit=100 (기본값: 전체 최신까지)
     */
    @PostMapping("/sync")
    public ResponseEntity<Map<String, Object>> syncDonghaeng(@RequestParam(name = "limit", defaultValue = "0") int limit) {
        int synced = systemService.syncFromDonghaengApi(limit);
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "syncedCount", synced,
                "message", synced + "개 회차 동기화가 완료되었습니다."
        ));
    }

    /**
     * 동행복권 엑셀 파일 수동 업로드 및 DB 일괄 적재
     * POST /system/manual/excel
     */
    @PostMapping(path = "/manual/excel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> manualExcel(@RequestParam("file") MultipartFile file) throws IOException {
        Map<String, Object> result = systemService.parseAndSaveExcel(file);
        return ResponseEntity.ok(result);
    }
}
