package com.legalcase.dto.response;

import com.legalcase.entity.AuditLog;
import com.legalcase.enums.AuditAction;
import com.legalcase.enums.AuditStatus;
import com.legalcase.enums.EntityType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AuditLogResponse {

    private Long id;
    private Long userId;
    private String userIdentifier;
    private String userName;
    private AuditAction action;
    private EntityType entityType;
    private Long entityId;
    private String entityIdentifier;
    private String beforeValue;
    private String afterValue;
    private String details;
    private String ipAddress;
    private String userAgent;
    private AuditStatus status;
    private String errorMessage;
    private LocalDateTime createdAt;
    private String summary;

    public static AuditLogResponse fromEntity(AuditLog auditLog) {
        return AuditLogResponse.builder()
                .id(auditLog.getId())
                .userId(auditLog.getUserId())
                .userIdentifier(auditLog.getUserIdentifier())
                .userName(auditLog.getUserName())
                .action(auditLog.getAction())
                .entityType(auditLog.getEntityType())
                .entityId(auditLog.getEntityId())
                .entityIdentifier(auditLog.getEntityIdentifier())
                .beforeValue(auditLog.getBeforeValue())
                .afterValue(auditLog.getAfterValue())
                .details(auditLog.getDetails())
                .ipAddress(auditLog.getIpAddress())
                .userAgent(auditLog.getUserAgent())
                .status(auditLog.getStatus())
                .errorMessage(auditLog.getErrorMessage())
                .createdAt(auditLog.getCreatedAt())
                .summary(auditLog.getSummary())
                .build();
    }
}