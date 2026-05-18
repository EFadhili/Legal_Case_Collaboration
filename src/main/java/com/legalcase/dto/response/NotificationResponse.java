package com.legalcase.dto.response;

import com.legalcase.entity.Notification;
import com.legalcase.enums.NotificationPriority;
import com.legalcase.enums.NotificationStatus;
import com.legalcase.enums.NotificationType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class NotificationResponse {

    private Long id;
    private NotificationType type;
    private NotificationPriority priority;
    private NotificationStatus status;
    private String title;
    private String message;
    private Long caseId;
    private String caseNumber;
    private String caseTitle;
    private Long taskId;
    private String taskTitle;
    private Long messageId;
    private Long actorId;
    private String actorName;
    private String actionUrl;
    private LocalDateTime createdAt;
    private LocalDateTime readAt;

    public static NotificationResponse fromEntity(Notification notification) {
        NotificationResponseBuilder builder = NotificationResponse.builder()
                .id(notification.getId())
                .type(notification.getType())
                .priority(notification.getPriority())
                .status(notification.getStatus())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .caseId(notification.getCaseId())
                .caseNumber(notification.getCaseNumber())
                .caseTitle(notification.getCaseTitle())
                .taskId(notification.getTaskId())
                .taskTitle(notification.getTaskTitle())
                .messageId(notification.getMessageId())
                .actorId(notification.getActorId())
                .actorName(notification.getActorName())
                .actionUrl(notification.getActionUrl())
                .createdAt(notification.getCreatedAt())
                .readAt(notification.getReadAt());

        return builder.build();
    }
}