package com.legalcase.exception;

import com.legalcase.dto.response.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ============================================
    // 400 BAD REQUEST - Validation Errors
    // ============================================

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(
            MethodArgumentNotValidException ex,
            WebRequest request) {

        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        log.warn("Validation error: {}", errors);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.validationError(getPath(request), errors));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            WebRequest request) {

        String message = String.format("Parameter '%s' should be of type %s",
                ex.getName(), ex.getRequiredType().getSimpleName());

        log.warn("Type mismatch: {}", message);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(400, "Bad Request", message, getPath(request)));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParams(
            MissingServletRequestParameterException ex,
            WebRequest request) {

        String message = String.format("Required parameter '%s' is missing", ex.getParameterName());

        log.warn("Missing parameter: {}", message);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(400, "Bad Request", message, getPath(request)));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable(
            HttpMessageNotReadableException ex,
            WebRequest request) {

        log.warn("Malformed JSON request: {}", ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(400, "Bad Request",
                        "Malformed JSON request", getPath(request)));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxSizeException(
            MaxUploadSizeExceededException ex,
            WebRequest request) {

        log.warn("File size exceeds limit: {}", ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(400, "Bad Request",
                        "File size exceeds the maximum allowed limit (100MB)", getPath(request)));
    }

    // ============================================
    // 401 UNAUTHORIZED - Authentication Errors
    // ============================================

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(
            UnauthorizedException ex,
            WebRequest request) {

        log.warn("Unauthorized access: {}", ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of(401, "Unauthorized", ex.getMessage(),
                        getPath(request), ex.getErrorCode()));
    }

    // ============================================
    // 403 FORBIDDEN - Access Denied
    // ============================================

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException ex,
            WebRequest request) {

        log.warn("Access denied: {}", ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of(403, "Forbidden", ex.getMessage(),
                        getPath(request), "ACCESS_DENIED"));
    }

    // ============================================
    // 404 NOT FOUND - Resource Not Found
    // ============================================

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            ResourceNotFoundException ex,
            WebRequest request) {

        log.info("Resource not found: {}", ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(404, "Not Found", ex.getMessage(),
                        getPath(request), ex.getErrorCode()));
    }

    // ============================================
    // 409 CONFLICT - Duplicate Resources
    // ============================================

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ErrorResponse> handleDuplicate(
            DuplicateResourceException ex,
            WebRequest request) {

        log.warn("Duplicate resource: {}", ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(409, "Conflict", ex.getMessage(),
                        getPath(request), ex.getErrorCode()));
    }

    // ============================================
    // 422 UNPROCESSABLE ENTITY - Business Rules
    // ============================================

    @ExceptionHandler(InvalidStatusTransitionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidTransition(
            InvalidStatusTransitionException ex,
            WebRequest request) {

        log.warn("Invalid status transition: {}", ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of(422, "Unprocessable Entity", ex.getMessage(),
                        getPath(request), ex.getErrorCode()));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(
            BusinessException ex,
            WebRequest request) {

        log.warn("Business rule violation: {}", ex.getMessage());

        int status = mapBusinessExceptionToStatus(ex);
        String errorName = mapBusinessExceptionToErrorName(ex);

        return ResponseEntity
                .status(status)
                .body(ErrorResponse.of(status, errorName, ex.getMessage(),
                        getPath(request), ex.getErrorCode()));
    }

    // ============================================
    // 500 INTERNAL SERVER ERROR
    // ============================================

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex,
            WebRequest request) {

        log.error("Unexpected error occurred: ", ex);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(500, "Internal Server Error",
                        "An unexpected error occurred. Please try again later.",
                        getPath(request)));
    }

    // ============================================
    // Helper Methods
    // ============================================

    private String getPath(WebRequest request) {
        return request.getDescription(false).replace("uri=", "");
    }

    private int mapBusinessExceptionToStatus(BusinessException ex) {
        switch (ex.getErrorCode()) {
            case "VALIDATION_ERROR":
                return 400;
            case "UNAUTHORIZED":
                return 401;
            case "ACCESS_DENIED":
                return 403;
            case "RESOURCE_NOT_FOUND":
                return 404;
            case "DUPLICATE_RESOURCE":
                return 409;
            case "INVALID_STATUS_TRANSITION":
                return 422;
            default:
                return 400;
        }
    }

    private String mapBusinessExceptionToErrorName(BusinessException ex) {
        switch (ex.getErrorCode()) {
            case "VALIDATION_ERROR":
                return "Validation Failed";
            case "UNAUTHORIZED":
                return "Unauthorized";
            case "ACCESS_DENIED":
                return "Access Denied";
            case "RESOURCE_NOT_FOUND":
                return "Not Found";
            case "DUPLICATE_RESOURCE":
                return "Conflict";
            case "INVALID_STATUS_TRANSITION":
                return "Invalid Status Transition";
            default:
                return "Business Rule Violation";
        }
    }
}