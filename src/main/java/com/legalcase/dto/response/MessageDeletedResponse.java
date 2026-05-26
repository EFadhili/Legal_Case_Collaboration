package com.legalcase.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class MessageDeletedResponse {
    private Long messageId;
    private Long caseId;
    private String caseNumber;
    private Long deletedBy;
    private String deletedByName;
    private LocalDateTime deletedAt;
    private String reason;
    private boolean isDeleted;
}