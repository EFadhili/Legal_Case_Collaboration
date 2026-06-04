package com.legalcase.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legalcase.annotation.Auditable;
import com.legalcase.entity.AuditLog;
import com.legalcase.enums.AuditStatus;
import com.legalcase.repository.AuditLogRepository;
import com.legalcase.service.AuditService;
import com.legalcase.util.AuditContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditAspect {

    private final AuditService auditService;

    @Around("@annotation(com.legalcase.annotation.Auditable)")
    public Object audit(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Auditable auditable = method.getAnnotation(Auditable.class);

        Object beforeState = null;
        Object result = null;
        boolean success = false;
        String errorMessage = null;

        try {
            // Capture state before if it's an update
            if (auditable.captureBefore()) {
                beforeState = captureBeforeState(joinPoint, auditable);
            }

            // Execute the method
            result = joinPoint.proceed();

            // Capture state after
            Object afterState = null;
            if (auditable.captureAfter()) {
                afterState = captureAfterState(joinPoint, result, auditable);
            }

            success = true;

            // Record audit log asynchronously
            auditService.recordAuditAsync(
                    AuditContext.getCurrentUserId(),
                    AuditContext.getCurrentUserIdentifier(),
                    AuditContext.getCurrentUserName(),
                    auditable.action(),
                    auditable.entityType(),
                    extractEntityId(joinPoint, result),
                    extractEntityIdentifier(joinPoint, result),
                    beforeState,
                    afterState,
                    null,  // details
                    AuditStatus.SUCCESS,
                    null,  // error message
                    AuditContext.getCurrentIpAddress(),
                    AuditContext.getCurrentUserAgent()
            );

            return result;

        } catch (Exception e) {
            errorMessage = e.getMessage();
            throw e;
        } finally {
            if (!success) {
                // Record failure
                auditService.recordAuditAsync(
                        AuditContext.getCurrentUserId(),
                        AuditContext.getCurrentUserIdentifier(),
                        AuditContext.getCurrentUserName(),
                        auditable.action(),
                        auditable.entityType(),
                        extractEntityId(joinPoint, result),
                        extractEntityIdentifier(joinPoint, result),
                        beforeState,
                        null,
                        null,
                        AuditStatus.FAILURE,
                        errorMessage,
                        AuditContext.getCurrentIpAddress(),
                        AuditContext.getCurrentUserAgent()
                );
            }
        }
    }

    private Object captureBeforeState(ProceedingJoinPoint joinPoint, Auditable auditable) {
        // Implement based on your entity structure
        // This should capture the entity before modification
        // For now, return null - implement based on your needs
        return null;
    }

    private Object captureAfterState(ProceedingJoinPoint joinPoint, Object result, Auditable auditable) {
        // Implement based on your entity structure
        // This should capture the entity after modification
        // For now, return result
        return result;
    }

    private Long extractEntityId(ProceedingJoinPoint joinPoint, Object result) {
        // Extract entity ID from method arguments or result
        // Common pattern: first argument is the ID, or result has getId()
        Object[] args = joinPoint.getArgs();
        if (args.length > 0 && args[0] instanceof Long) {
            return (Long) args[0];
        }
        if (result != null && result instanceof com.legalcase.entity.User) {
            return ((com.legalcase.entity.User) result).getId();
        }
        return null;
    }

    private String extractEntityIdentifier(ProceedingJoinPoint joinPoint, Object result) {
        // Extract human-readable identifier (case number, task number, etc.)
        // Implement based on your entity types
        return null;
    }
}