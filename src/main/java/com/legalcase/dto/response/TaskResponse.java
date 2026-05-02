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
    private String title;
    private String description;
    private TaskType type;
    private TaskPriority priority;
    private TaskStatus status;
    private Integer progress;
    private LocalDate dueDate;
    private Long caseId;
    private String caseNumber;
    private String caseTitle;
    private Long assignedToId;
    private String assignedToName;
    private Long createdById;
    private String createdByName;
    private Long dependsOnTaskId;
    private String dependsOnTaskTitle;
    private Long approvedById;
    private String approvedByName;
    private LocalDateTime approvedAt;
    private boolean isBlocked;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static TaskResponse fromEntity(Task task) {
        TaskResponseBuilder builder = TaskResponse.builder()
                .id(task.getId())
                .title(task.getTitle())
                .description(task.getDescription())
                .type(task.getType())
                .priority(task.getPriority())
                .status(task.getStatus())
                .progress(task.getProgress())
                .dueDate(task.getDueDate())
                .caseId(task.getLegalCase().getId())
                .caseNumber(task.getLegalCase().getCaseNumber())
                .caseTitle(task.getLegalCase().getTitle())
                .createdById(task.getCreatedBy().getId())
                .createdByName(task.getCreatedBy().getFullName())
                .isBlocked(task.isBlockedByDependency())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt());

        if (task.getAssignedTo() != null) {
            builder.assignedToId(task.getAssignedTo().getId())
                    .assignedToName(task.getAssignedTo().getFullName());
        }

        if (task.getDependsOn() != null) {
            builder.dependsOnTaskId(task.getDependsOn().getId())
                    .dependsOnTaskTitle(task.getDependsOn().getTitle());
        }

        if (task.getApprovedBy() != null) {
            builder.approvedById(task.getApprovedBy().getId())
                    .approvedByName(task.getApprovedBy().getFullName())
                    .approvedAt(task.getApprovedAt());
        }

        return builder.build();
    }
}