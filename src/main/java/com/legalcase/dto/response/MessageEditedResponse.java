package com.legalcase.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class MessageEditedResponse {
    private Long messageId;
    private Long caseId;
    private String caseNumber;
    private String oldContent;
    private String newContent;
    private Long editedBy;
    private String editedByName;
    private LocalDateTime editedAt;
    private String reason;
    private boolean isEdited;
}