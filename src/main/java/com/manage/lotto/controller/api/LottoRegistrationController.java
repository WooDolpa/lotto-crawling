package com.manage.lotto.controller.api;

import com.manage.lotto.dto.*;
import com.manage.lotto.service.LottoHistoryRegistrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.Map;

@RestController
@RequestMapping("/api/lotto/history")
@RequiredArgsConstructor
public class LottoRegistrationController {
    private final LottoHistoryRegistrationService service;
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LottoHistoryResponse register(@RequestBody LottoHistoryCreateRequest request) { return service.register(request); }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String,String>> invalid(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("message", e.getReason() == null ? "등록 실패" : e.getReason()));
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String,String> malformed() { return Map.of("message", "입력 형식이 올바르지 않습니다."); }
    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String,String> conflict() { return Map.of("message", "이미 등록된 회차이거나 저장 제약 조건을 만족하지 않습니다."); }
}
