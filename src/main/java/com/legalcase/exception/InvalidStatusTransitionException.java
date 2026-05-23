package com.legalcase.exception;

public class InvalidStatusTransitionException extends BusinessException {

    public InvalidStatusTransitionException(String currentStatus, String targetStatus) {
        super(String.format("Invalid status transition from %s to %s", currentStatus, targetStatus),
                "INVALID_STATUS_TRANSITION", currentStatus, targetStatus);
    }

    public InvalidStatusTransitionException(String message) {
        super(message, "INVALID_STATUS_TRANSITION");
    }
}