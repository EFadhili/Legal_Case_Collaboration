package com.legalcase.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class CommentEditedResponse {
    private Long commentId;
    private Long caseId;
    private String caseNumber;
    private Long taskId;
    private String taskTitle;
    private String oldContent;
    private String newContent;
    private Long editedBy;
    private String editedByName;
    private LocalDateTime editedAt;
    private String reason;
    private boolean isEdited;
}