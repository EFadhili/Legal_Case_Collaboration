package com.legalcase.dto.request;

import com.legalcase.enums.CaseStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateCaseStatusRequest {

    @NotNull(message = "New status is required")
    private CaseStatus status;
}