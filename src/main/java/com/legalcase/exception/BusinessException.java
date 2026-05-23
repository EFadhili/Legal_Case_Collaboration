package com.legalcase.exception;

import lombok.Getter;

/**
 * Base business exception for application-specific errors.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final String errorCode;
    private final Object[] args;

    public BusinessException(String message) {
        super(message);
        this.errorCode = "BUSINESS_ERROR";
        this.args = null;
    }

    public BusinessException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
        this.args = null;
    }

    public BusinessException(String message, String errorCode, Object... args) {
        super(message);
        this.errorCode = errorCode;
        this.args = args;
    }
}