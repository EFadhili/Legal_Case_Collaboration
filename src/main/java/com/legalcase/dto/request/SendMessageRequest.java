package com.legalcase.dto.request;

import com.legalcase.enums.MessageType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class SendMessageRequest {

    @NotBlank(message = "Message content is required")
    private String content;

    @NotNull(message = "Case ID is required")
    private Long caseId;

    private MessageType type = MessageType.TEXT;

    private String fileUrl;
    private String fileName;
    private Long fileSize;

    // Mentions
    private List<Long> mentionedUserIds;
    private List<Long> mentionedTaskIds;
}

