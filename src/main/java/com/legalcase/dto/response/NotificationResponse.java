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

    // Context references
    private Long caseId;
    private String caseNumber;
    private String caseTitle;
    private Long taskId;
    private String taskTitle;
    private Long commentId;
    private Long messageId;
    private Long documentId;
    private Long interactionId;

    // Actor information
    private Long actorId;
    private String actorName;

    private String actionUrl;
    private LocalDateTime createdAt;
    private LocalDateTime readAt;
    private LocalDateTime archivedAt;

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
                .commentId(notification.getCommentId())
                .messageId(notification.getMessageId())
                .documentId(notification.getDocumentId())
                .interactionId(notification.getInteractionId())
                .actorId(notification.getActorId())
                .actorName(notification.getActorName())
                .actionUrl(notification.getActionUrl())
                .createdAt(notification.getCreatedAt())
                .readAt(notification.getReadAt())
                .archivedAt(notification.getArchivedAt());

        return builder.build();
    }
}