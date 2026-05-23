package com.legalcase.exception;

public class ResourceNotFoundException extends BusinessException {

    public ResourceNotFoundException(String resourceName, Long id) {
        super(String.format("%s not found with ID: %d", resourceName, id),
                "RESOURCE_NOT_FOUND", resourceName, id);
    }

    public ResourceNotFoundException(String resourceName, String field, String value) {
        super(String.format("%s not found with %s: %s", resourceName, field, value),
                "RESOURCE_NOT_FOUND", resourceName, field, value);
    }

    public ResourceNotFoundException(String message) {
        super(message, "RESOURCE_NOT_FOUND");
    }
}