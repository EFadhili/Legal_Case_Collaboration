package com.legalcase.dto.response;

import com.legalcase.entity.Task;
import com.legalcase.enums.TaskPriority;
import com.legalcase.enums.TaskStatus;
import com.legalcase.enums.TaskType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class TaskResponse {

    private Long id;
    private String taskNumber;  // NEW - Human-readable task identifier
    private String title;
    private String description;
    private TaskType type;
    private TaskPriority priority;
    private TaskStatus status;
    private Integer progress;
    private LocalDate dueDate;

    // Case information
    private Long caseId;
    private String caseNumber;
    private String caseTitle;

    // Assigned to user
    private Long assignedToId;
    private String assignedToName;
    private String assignedToUsername;  // NEW

    // Created by user
    private Long createdById;
    private String createdByName;
    private String createdByUsername;    // NEW

    // Dependency
    private Long dependsOnTaskId;
    private String dependsOnTaskTitle;

    // Approved by user
    private Long approvedById;
    private String approvedByName;
    private String approvedByUsername;   // NEW

    private LocalDateTime approvedAt;
    private boolean isBlocked;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static TaskResponse fromEntity(Task task) {
        TaskResponseBuilder builder = TaskResponse.builder()
                .id(task.getId())
                .taskNumber(task.getTaskNumber())  // NEW
                .title(task.getTitle())
                .description(task.getDescription())
                .type(task.getType())
                .priority(task.getPriority())
                .status(task.getStatus())
                .progress(task.getProgress())
                .dueDate(task.getDueDate())
                .isBlocked(task.isBlockedByDependency())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt());

        // Case information
        if (task.getLegalCase() != null) {
            builder.caseId(task.getLegalCase().getId())
                    .caseNumber(task.getLegalCase().getCaseNumber())
                    .caseTitle(task.getLegalCase().getTitle());
        }

        // Created by user
        if (task.getCreatedBy() != null) {
            builder.createdById(task.getCreatedBy().getId())
                    .createdByName(task.getCreatedBy().getFullName())
                    .createdByUsername(task.getCreatedBy().getUsername());
        }

        // Assigned to user
        if (task.getAssignedTo() != null) {
            builder.assignedToId(task.getAssignedTo().getId())
                    .assignedToName(task.getAssignedTo().getFullName())
                    .assignedToUsername(task.getAssignedTo().getUsername());
        }

        // Dependency
        if (task.getDependsOn() != null) {
            builder.dependsOnTaskId(task.getDependsOn().getId())
                    .dependsOnTaskTitle(task.getDependsOn().getTitle());
        }

        // Approved by user
        if (task.getApprovedBy() != null) {
            builder.approvedById(task.getApprovedBy().getId())
                    .approvedByName(task.getApprovedBy().getFullName())
                    .approvedByUsername(task.getApprovedBy().getUsername())
                    .approvedAt(task.getApprovedAt());
        }

        return builder.build();
    }
}