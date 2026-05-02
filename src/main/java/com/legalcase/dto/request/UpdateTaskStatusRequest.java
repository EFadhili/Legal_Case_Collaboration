package com.legalcase.dto.request;

import com.legalcase.enums.TaskStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateTaskStatusRequest {

    @NotNull(message = "New status is required")
    private TaskStatus status;
}