package com.account.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class ValidationException extends RuntimeException {

    private final String errorCode;
    private final String field; // Optional: which field caused error

    public ValidationException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
        this.field = null;
    }

    public ValidationException(String message, String errorCode, String field) {
        super(message);
        this.errorCode = errorCode;
        this.field = field;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getField() {
        return field;
    }
}