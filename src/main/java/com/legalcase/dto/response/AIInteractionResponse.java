package com.legalcase.dto.response;

import com.legalcase.entity.AIInteraction;
import com.legalcase.enums.AIQueryType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AIInteractionResponse {

    private Long id;
    private Long userId;
    private String userName;
    private Long caseId;
    private String caseNumber;
    private String caseTitle;
    private AIQueryType queryType;
    private String userPrompt;
    private String aiResponse;
    private Integer tokenCountInput;
    private Integer tokenCountOutput;
    private Long processingTimeMs;
    private Integer userRating;
    private LocalDateTime createdAt;

    public static AIInteractionResponse fromEntity(AIInteraction interaction) {
        AIInteractionResponseBuilder builder = AIInteractionResponse.builder()
                .id(interaction.getId())
                .userId(interaction.getUser().getId())
                .userName(interaction.getUser().getFullName())
                .queryType(interaction.getQueryType())
                .userPrompt(interaction.getUserPrompt())
                .aiResponse(interaction.getAiResponse())
                .tokenCountInput(interaction.getTokenCountInput())
                .tokenCountOutput(interaction.getTokenCountOutput())
                .processingTimeMs(interaction.getProcessingTimeMs())
                .userRating(interaction.getUserRating())
                .createdAt(interaction.getCreatedAt());

        if (interaction.getLegalCase() != null) {
            builder.caseId(interaction.getLegalCase().getId())
                    .caseNumber(interaction.getLegalCase().getCaseNumber())
                    .caseTitle(interaction.getLegalCase().getTitle());
        }

        return builder.build();
    }
}