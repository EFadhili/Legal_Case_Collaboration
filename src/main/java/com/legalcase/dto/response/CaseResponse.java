package com.legalcase.dto.response;

import com.legalcase.entity.LegalCase;
import com.legalcase.enums.CasePriority;
import com.legalcase.enums.CaseStatus;
import com.legalcase.enums.CaseType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Data
@Builder
public class CaseResponse {

    private Long id;
    private String caseNumber;
    private String title;
    private String description;
    private CaseStatus status;
    private CasePriority priority;
    private CaseType type;
    private LocalDate dueDate;
    private LocalDate filingDate;
    private LocalDate hearingDate;
    private boolean isLocked;
    private String ownerName;
    private Long ownerId;
    private List<MemberResponse> members;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Progress indicators for UI
    private boolean readyForInProgress;
    private boolean readyForClosed;
    private Integer mandatoryTaskCount;
    private Integer completedMandatoryTaskCount;
    private Integer progressPercentage;

    public static CaseResponse fromEntity(LegalCase legalCase, List<MemberResponse> members) {
        return CaseResponse.builder()
                .id(legalCase.getId())
                .caseNumber(legalCase.getCaseNumber())
                .title(legalCase.getTitle())
                .description(legalCase.getDescription())
                .status(legalCase.getStatus())
                .priority(legalCase.getPriority())
                .type(legalCase.getType())
                .dueDate(legalCase.getDueDate())
                .filingDate(legalCase.getFilingDate())
                .hearingDate(legalCase.getHearingDate())
                .isLocked(legalCase.isLocked())
                .ownerName(legalCase.getOwner().getFullName())
                .ownerId(legalCase.getOwner().getId())
                .members(members)
                .createdAt(legalCase.getCreatedAt())
                .updatedAt(legalCase.getUpdatedAt())
                .build();
    }
}