package com.legalcase.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class DeleteCommentResponse {
    private Long commentId;
    private Long caseId;
    private String caseNumber;
    private Long taskId;
    private String taskTitle;
    private Long deletedBy;
    private String deletedByName;
    private LocalDateTime deletedAt;
    private String reason;
    private boolean isDeleted;
}