package com.manage.lotto.exception;

public class ValidationInProgressException extends RuntimeException {
    public ValidationInProgressException(String message) {
        super(message);
    }
}
