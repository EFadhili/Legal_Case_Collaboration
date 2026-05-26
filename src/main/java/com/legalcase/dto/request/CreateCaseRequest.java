package com.legalcase.dto.request;

import com.legalcase.enums.CasePriority;
import com.legalcase.enums.CaseType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.Set;

@Data
public class CreateCaseRequest {

    @NotBlank(message = "Case title is required")
    private String title;

    private String description;

    @NotNull(message = "Case type is required")
    private CaseType type;

    @NotNull(message = "Case priority is required")
    private CasePriority priority = CasePriority.MEDIUM;

    private LocalDate dueDate;

    private Set<String> assignedUserIdentifiers;  // Changed from Set<Long> assignedUserIds
}