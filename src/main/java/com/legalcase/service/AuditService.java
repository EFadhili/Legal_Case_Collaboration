package com.legalcase.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legalcase.entity.AuditLog;
import com.legalcase.enums.AuditAction;
import com.legalcase.enums.AuditStatus;
import com.legalcase.enums.EntityType;
import com.legalcase.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    @Async
    @Transactional
    public void recordAuditAsync(Long userId, String userIdentifier, String userName,
                                 AuditAction action, EntityType entityType,
                                 Long entityId, String entityIdentifier,
                                 Object beforeState, Object afterState,
                                 String details, AuditStatus status,
                                 String errorMessage, String ipAddress,
                                 String userAgent) {
        try {
            String beforeJson = beforeState != null ? objectMapper.writeValueAsString(beforeState) : null;
            String afterJson = afterState != null ? objectMapper.writeValueAsString(afterState) : null;
            String detailsJson = details != null ? details : null;

            AuditLog auditLog = AuditLog.builder()
                    .userId(userId)
                    .userIdentifier(userIdentifier)
                    .userName(userName)
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .entityIdentifier(entityIdentifier)
                    .beforeValue(beforeJson)
                    .afterValue(afterJson)
                    .details(detailsJson)
                    .ipAddress(ipAddress)
                    .userAgent(userAgent)
                    .status(status)
                    .errorMessage(errorMessage)
                    .build();

            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Failed to record audit log: {}", e.getMessage(), e);
        }
    }

    @Transactional
    public void recordAuditSync(Long userId, String userIdentifier, String userName,
                                AuditAction action, EntityType entityType,
                                Long entityId, String entityIdentifier,
                                Object beforeState, Object afterState,
                                String details, AuditStatus status,
                                String errorMessage, String ipAddress,
                                String userAgent) {
        try {
            String beforeJson = beforeState != null ? objectMapper.writeValueAsString(beforeState) : null;
            String afterJson = afterState != null ? objectMapper.writeValueAsString(afterState) : null;
            String detailsJson = details != null ? details : null;

            AuditLog auditLog = AuditLog.builder()
                    .userId(userId)
                    .userIdentifier(userIdentifier)
                    .userName(userName)
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .entityIdentifier(entityIdentifier)
                    .beforeValue(beforeJson)
                    .afterValue(afterJson)
                    .details(detailsJson)
                    .ipAddress(ipAddress)
                    .userAgent(userAgent)
                    .status(status)
                    .errorMessage(errorMessage)
                    .build();

            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Failed to record audit log: {}", e.getMessage(), e);
        }
    }

    // ============================================
    // QUERY METHODS (Admin only)
    // ============================================

    public Page<AuditLog> getAuditLogsByUser(Long userId, Pageable pageable) {
        return auditLogRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    public Page<AuditLog> getAuditLogsByAction(AuditAction action, Pageable pageable) {
        return auditLogRepository.findByActionOrderByCreatedAtDesc(action, pageable);
    }

    public Page<AuditLog> getAuditLogsByEntity(EntityType entityType, Long entityId, Pageable pageable) {
        return auditLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(entityType, entityId, pageable);
    }

    public Page<AuditLog> getAuditLogsByDateRange(LocalDateTime startDate, LocalDateTime endDate, Pageable pageable) {
        return auditLogRepository.findByDateRange(startDate, endDate, pageable);
    }

    public Page<AuditLog> searchAuditLogs(Long userId, AuditAction action,
                                          EntityType entityType, Long entityId,
                                          AuditStatus status,
                                          LocalDateTime startDate, LocalDateTime endDate,
                                          Pageable pageable) {
        return auditLogRepository.search(userId, action, entityType, entityId, status, startDate, endDate, pageable);
    }

    public List<AuditLog> getRecentFailures(int limit) {
        return auditLogRepository.findRecentFailures(Pageable.ofSize(limit));
    }

    // ============================================
    // STATISTICS
    // ============================================

    public long getTotalAuditCount() {
        return auditLogRepository.count();
    }

    public long getAuditCountByUser(Long userId) {
        return auditLogRepository.countByUserId(userId);
    }

    public long getAuditCountByAction(AuditAction action) {
        return auditLogRepository.countByAction(action);
    }
}