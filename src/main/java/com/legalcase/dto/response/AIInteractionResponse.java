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
    private String interactionNumber;
    private Long userId;
    private String userIdentifier;
    private String userName;
    private Long caseId;
    private String caseNumber;
    private String caseTitle;
    private AIQueryType queryType;
    private String userPrompt;
    private String aiResponsePreview;
    private Integer tokenCountInput;
    private Integer tokenCountOutput;
    private Long processingTimeMs;
    private Integer userRating;
    private LocalDateTime createdAt;

    // Soft delete fields
    private boolean isDeleted;
    private LocalDateTime deletedAt;
    private String deletedReason;

    // Edit tracking for rating
    private LocalDateTime ratingUpdatedAt;
    private String ratingUpdatedBy;

    public static AIInteractionResponse fromEntity(AIInteraction interaction) {
        AIInteractionResponseBuilder builder = AIInteractionResponse.builder()
                .id(interaction.getId())
                .interactionNumber(interaction.getInteractionNumber())
                .userId(interaction.getUser().getId())
                .userIdentifier(interaction.getUser().getUsername())
                .userName(interaction.getUser().getFullName())
                .queryType(interaction.getQueryType())
                .userPrompt(interaction.getUserPrompt())
                .aiResponsePreview(interaction.getAiResponse().length() > 200 ?
                        interaction.getAiResponse().substring(0, 200) + "..." :
                        interaction.getAiResponse())
                .tokenCountInput(interaction.getTokenCountInput())
                .tokenCountOutput(interaction.getTokenCountOutput())
                .processingTimeMs(interaction.getProcessingTimeMs())
                .userRating(interaction.getUserRating())
                .createdAt(interaction.getCreatedAt())
                .isDeleted(interaction.isDeleted())
                .deletedAt(interaction.getDeletedAt())
                .deletedReason(interaction.getDeletedReason())
                .ratingUpdatedAt(interaction.getRatingUpdatedAt());

        if (interaction.getLegalCase() != null) {
            builder.caseId(interaction.getLegalCase().getId())
                    .caseNumber(interaction.getLegalCase().getCaseNumber())
                    .caseTitle(interaction.getLegalCase().getTitle());
        }

        return builder.build();
    }
}