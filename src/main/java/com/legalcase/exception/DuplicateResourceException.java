package com.legalcase.exception;

public class DuplicateResourceException extends BusinessException {

    public DuplicateResourceException(String resourceName, String field, String value) {
        super(String.format("%s already exists with %s: %s", resourceName, field, value),
                "DUPLICATE_RESOURCE", resourceName, field, value);
    }

    public DuplicateResourceException(String message) {
        super(message, "DUPLICATE_RESOURCE");
    }
}