package com.legalcase.exception;

import lombok.Getter;

import java.util.Map;

@Getter
public class ValidationException extends BusinessException {

    private final Map<String, String> errors;

    public ValidationException(Map<String, String> errors) {
        super("Validation failed", "VALIDATION_ERROR");
        this.errors = errors;
    }

    public ValidationException(String field, String message) {
        super("Validation failed", "VALIDATION_ERROR");
        this.errors = Map.of(field, message);
    }
}