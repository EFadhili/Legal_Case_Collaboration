package com.legalcase.entity;

import com.legalcase.enums.AIQueryType;
import com.legalcase.enums.AIResponseFormat;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "ai_interactions")
@Data
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class AIInteraction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id")
    private LegalCase legalCase;

    @Enumerated(EnumType.STRING)
    @Column(name = "query_type")
    private AIQueryType queryType;

    @Column(name = "user_prompt", nullable = false, columnDefinition = "TEXT")
    private String userPrompt;

    @Column(name = "ai_response", nullable = false, columnDefinition = "TEXT")
    private String aiResponse;

    @Column(name = "response_format")
    @Enumerated(EnumType.STRING)
    private AIResponseFormat responseFormat = AIResponseFormat.TEXT;

    @Column(name = "context_document_ids")
    private String contextDocumentIds;  // Comma-separated document IDs used as context

    @Column(name = "model_used")
    private String modelUsed = "gemini-1.5-pro";

    @Column(name = "token_count_input")
    private Integer tokenCountInput;

    @Column(name = "token_count_output")
    private Integer tokenCountOutput;

    @Column(name = "processing_time_ms")
    private Long processingTimeMs;

    @Column(name = "was_cached")
    private boolean wasCached = false;

    @Column(name = "user_rating")
    private Integer userRating;  // 1-5 stars for feedback

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    // Helper methods
    public Set<Long> getContextDocumentIdsSet() {
        if (contextDocumentIds == null || contextDocumentIds.isEmpty()) {
            return new HashSet<>();
        }
        Set<Long> ids = new HashSet<>();
        for (String id : contextDocumentIds.split(",")) {
            ids.add(Long.parseLong(id.trim()));
        }
        return ids;
    }

    public void setContextDocumentIdsFromSet(Set<Long> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            this.contextDocumentIds = null;
        } else {
            this.contextDocumentIds = documentIds.stream()
                    .map(String::valueOf)
                    .reduce((a, b) -> a + "," + b)
                    .orElse("");
        }
    }
}


