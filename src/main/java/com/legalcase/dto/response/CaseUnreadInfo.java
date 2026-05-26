package com.legalcase.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class CaseUnreadInfo {
    private Long caseId;
    private String caseNumber;
    private String caseTitle;
    private Long unreadCount;
    private LocalDateTime lastMessageAt;
    private String lastMessagePreview;
    private Long lastMessageSenderId;
    private String lastMessageSenderName;
}
