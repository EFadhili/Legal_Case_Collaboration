package com.legalcase.dto.response;

import com.legalcase.enums.AuditAction;
import com.legalcase.enums.AuditStatus;
import com.legalcase.enums.EntityType;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class AuditStatisticsResponse {
    private long totalAudits;
    private Map<AuditAction, Long> countsByAction;
    private Map<EntityType, Long> countsByEntity;
    private Map<AuditStatus, Long> countsByStatus;
    private Map<Long, Long> countsByUser;
}