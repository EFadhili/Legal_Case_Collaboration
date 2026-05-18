package com.legalcase.dto.request;

import com.legalcase.enums.AIQueryType;
import com.legalcase.enums.AIResponseFormat;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Set;

@Data
public class AIQueryRequest {

    @NotBlank(message = "Prompt is required")
    private String prompt;

    private AIQueryType queryType = AIQueryType.GENERAL_QUESTION;

    private AIResponseFormat responseFormat = AIResponseFormat.TEXT;

    private Long caseId;  // Optional - for case-specific context

    private Set<Long> documentIds;  // Optional - specific documents to analyze

    private boolean useConversationHistory = false;  // For multi-turn conversations
}