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

    @NotNull(message = "Case identifier is required")
    private String caseIdentifier;  // Changed from caseId (Long) to caseIdentifier (String)

    private MessageType type = MessageType.TEXT;

    private String fileUrl;
    private String fileName;
    private Long fileSize;

    // Changed from userIds to user identifiers (usernames or emails)
    private List<String> mentionedUserIdentifiers;

    // Changed from taskIds to task identifiers (task numbers or IDs)
    private List<String> mentionedTaskIdentifiers;
}

