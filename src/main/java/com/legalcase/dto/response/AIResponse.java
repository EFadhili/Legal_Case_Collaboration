package com.legalcase.dto.response;

import com.legalcase.entity.AIInteraction;
import com.legalcase.enums.AIQueryType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AIResponse {

    private Long interactionId;
    private String answer;
    private AIQueryType queryType;
    private String modelUsed;
    private Integer tokenCount;
    private Long processingTimeMs;
    private LocalDateTime timestamp;
    private String disclaimer;

    public static AIResponse fromEntity(AIInteraction interaction) {
        return AIResponse.builder()
                .interactionId(interaction.getId())
                .answer(interaction.getAiResponse())
                .queryType(interaction.getQueryType())
                .modelUsed(interaction.getModelUsed())
                .tokenCount(interaction.getTokenCountOutput())
                .processingTimeMs(interaction.getProcessingTimeMs())
                .timestamp(interaction.getCreatedAt())
                .disclaimer(getDisclaimer())
                .build();
    }

    private static String getDisclaimer() {
        return "DISCLAIMER: This AI-generated response is for informational purposes only and does not constitute legal advice. " +
                "Always consult with a qualified legal professional for advice regarding your specific situation.";
    }
}