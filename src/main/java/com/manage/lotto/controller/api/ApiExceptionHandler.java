package com.manage.lotto.controller.api;

import com.manage.lotto.dto.ErrorResponse;
import com.manage.lotto.exception.DuplicateDrawException;
import com.manage.lotto.exception.InvalidLottoDataException;
import com.manage.lotto.exception.ModelNotReadyException;
import com.manage.lotto.exception.ValidationInProgressException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(InvalidLottoDataException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse invalid(InvalidLottoDataException e) {
        return new ErrorResponse(e.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse malformed() {
        return new ErrorResponse("입력 형식이 올바르지 않습니다.");
    }

    @ExceptionHandler(DuplicateDrawException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse duplicate(DuplicateDrawException e) {
        return new ErrorResponse(e.getMessage());
    }

    @ExceptionHandler(ValidationInProgressException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse validationInProgress(ValidationInProgressException e) {
        return new ErrorResponse(e.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse conflict() {
        return new ErrorResponse("이미 등록된 회차이거나 저장 제약 조건을 만족하지 않습니다.");
    }

    @ExceptionHandler(ModelNotReadyException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ErrorResponse modelNotReady(ModelNotReadyException e) {
        return new ErrorResponse(e.getMessage());
    }
}
