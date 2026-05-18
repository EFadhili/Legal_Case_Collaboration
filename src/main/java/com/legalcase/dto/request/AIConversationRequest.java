package com.legalcase.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class AIConversationRequest {

    @NotBlank(message = "Message is required")
    private String message;

    private Long caseId;

    private List<ChatHistory> history;  // For multi-turn conversations

    @Data
    public static class ChatHistory {
        private String role;  // "user" or "assistant"
        private String content;
    }
}