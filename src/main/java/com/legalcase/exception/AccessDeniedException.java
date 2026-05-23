package com.legalcase.exception;

public class AccessDeniedException extends BusinessException {

    public AccessDeniedException(String message) {
        super(message, "ACCESS_DENIED");
    }

    public AccessDeniedException() {
        super("Access denied. You do not have permission for this action.", "ACCESS_DENIED");
    }
}