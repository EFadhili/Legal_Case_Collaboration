package com.legalcase.exception;

public class FileProcessingException extends BusinessException {

    public FileProcessingException(String message) {
        super(message, "FILE_PROCESSING_ERROR");
    }

    public FileProcessingException(String message, Throwable cause) {
        super(message, "FILE_PROCESSING_ERROR");
        initCause(cause);
    }
}