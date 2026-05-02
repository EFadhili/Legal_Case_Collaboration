package com.legalcase.dto.request;

import com.legalcase.enums.TaskPriority;
import com.legalcase.enums.TaskType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CreateTaskRequest {

    @NotBlank(message = "Task title is required")
    private String title;

    private String description;

    @NotNull(message = "Task type is required")
    private TaskType type;

    @NotNull(message = "Task priority is required")
    private TaskPriority priority = TaskPriority.MEDIUM;

    private LocalDate dueDate;

    private Long assignedToUserId;

    private Long dependsOnTaskId;
}