package com.legalcase.service;

import com.legalcase.dto.response.NotificationResponse;
import com.legalcase.entity.*;
import com.legalcase.enums.NotificationPriority;
import com.legalcase.enums.NotificationStatus;
import com.legalcase.enums.NotificationType;
import com.legalcase.enums.Role;
import com.legalcase.exception.AccessDeniedException;
import com.legalcase.exception.ResourceNotFoundException;
import com.legalcase.repository.*;
import com.legalcase.util.AuditContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final CaseRepository caseRepository;
    private final TaskRepository taskRepository;
    private final CaseMemberRepository caseMemberRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final AuditService auditService;  // ADDED

    // ============================================
    // HELPER METHODS
    // ============================================

    private void recordAudit(com.legalcase.enums.AuditAction action,
                             com.legalcase.enums.EntityType entityType,
                             Long entityId, String entityIdentifier,
                             Object beforeState, Object afterState,
                             String details, boolean success, String errorMessage) {
        auditService.recordAuditAsync(
                AuditContext.getCurrentUserId(),
                AuditContext.getCurrentUserIdentifier(),
                AuditContext.getCurrentUserName(),
                action,
                entityType,
                entityId,
                entityIdentifier,
                beforeState,
                afterState,
                details,
                success ? com.legalcase.enums.AuditStatus.SUCCESS : com.legalcase.enums.AuditStatus.FAILURE,
                errorMessage,
                AuditContext.getCurrentIpAddress(),
                AuditContext.getCurrentUserAgent()
        );
    }

    private User findUserByIdentifier(String identifier) {
        return userRepository.findByUsername(identifier)
                .or(() -> userRepository.findByEmail(identifier))
                .orElseThrow(() -> new ResourceNotFoundException("User", "username or email", identifier));
    }

    private Long getUserIdFromIdentifier(String identifier) {
        return findUserByIdentifier(identifier).getId();
    }

    private void verifyNotificationAccess(Notification notification, Long userId) {
        if (!notification.getUser().getId().equals(userId)) {
            throw new AccessDeniedException("You don't have permission to access this notification");
        }
    }

    private void broadcastToUser(Long userId, Notification notification) {
        String userTopic = "/topic/notifications/" + userId;
        messagingTemplate.convertAndSend(userTopic, NotificationResponse.fromEntity(notification));
        log.debug("Broadcast notification to user topic: {}", userTopic);
    }

    private void broadcastToCase(Long caseId, Notification notification) {
        String caseTopic = "/topic/cases/" + caseId + "/notifications";
        messagingTemplate.convertAndSend(caseTopic, NotificationResponse.fromEntity(notification));
        log.debug("Broadcast notification to case topic: {}", caseTopic);
    }

    private Notification createNotificationInternal(Long userId, NotificationType type, NotificationPriority priority,
                                                    String title, String message, Long caseId, Long taskId,
                                                    Long commentId, Long messageId, Long documentId, Long interactionId,
                                                    Long actorId, String actionUrl) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        Notification notification = new Notification();
        notification.setUser(user);
        notification.setType(type);
        notification.setPriority(priority);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setCaseId(caseId);
        notification.setTaskId(taskId);
        notification.setCommentId(commentId);
        notification.setMessageId(messageId);
        notification.setDocumentId(documentId);
        notification.setInteractionId(interactionId);
        notification.setActorId(actorId);
        notification.setActionUrl(actionUrl);

        // Enrich with case details
        if (caseId != null) {
            caseRepository.findById(caseId).ifPresent(c -> {
                notification.setCaseNumber(c.getCaseNumber());
                notification.setCaseTitle(c.getTitle());
            });
        }

        // Enrich with task details
        if (taskId != null) {
            taskRepository.findById(taskId).ifPresent(t -> {
                notification.setTaskTitle(t.getTitle());
            });
        }

        // Enrich with actor details
        if (actorId != null) {
            userRepository.findById(actorId).ifPresent(actor -> {
                notification.setActorName(actor.getFullName());
            });
        }

        Notification saved = notificationRepository.save(notification);
        log.info("Created {} notification for user {}: {}", type, userId, title);

        // Broadcast via WebSocket
        broadcastToUser(userId, saved);
        if (caseId != null) {
            broadcastToCase(caseId, saved);
        }

        // AUDIT: Notification sent
        recordAudit(com.legalcase.enums.AuditAction.NOTIFICATION_SEND,
                com.legalcase.enums.EntityType.NOTIFICATION,
                saved.getId(),
                null,
                null,
                saved,
                "Notification type: " + type + ", user: " + userId,
                true,
                null);

        return saved;
    }

    // ============================================
    // CASE NOTIFICATIONS
    // ============================================

    public void notifyUserAddedToCase(Long userId, Long caseId, Long addedByUserId) {
        caseRepository.findById(caseId).ifPresent(c -> {
            String title = "Added to Case";
            String message = String.format("You have been added to case: %s", c.getTitle());
            String actionUrl = String.format("/cases/%d", caseId);

            createNotificationInternal(userId, NotificationType.ADDED_TO_CASE, NotificationPriority.MEDIUM,
                    title, message, caseId, null, null, null, null, null, addedByUserId, actionUrl);
        });
    }

    public void notifyUserRemovedFromCase(Long userId, Long caseId, Long removedByUserId) {
        caseRepository.findById(caseId).ifPresent(c -> {
            String title = "Removed from Case";
            String message = String.format("You have been removed from case: %s", c.getTitle());
            String actionUrl = String.format("/cases/%d", caseId);

            createNotificationInternal(userId, NotificationType.REMOVED_FROM_CASE, NotificationPriority.MEDIUM,
                    title, message, caseId, null, null, null, null, null, removedByUserId, actionUrl);
        });
    }

    public void notifyCaseDeadlineApproaching(Long caseId, Long ownerId, int daysRemaining) {
        caseRepository.findById(caseId).ifPresent(c -> {
            String title = "Case Deadline Approaching";
            String message = String.format("Case \"%s\" is due in %d days", c.getTitle(), daysRemaining);
            String actionUrl = String.format("/cases/%d", caseId);

            NotificationPriority priority = daysRemaining <= 1 ? NotificationPriority.URGENT :
                    (daysRemaining <= 3 ? NotificationPriority.HIGH : NotificationPriority.MEDIUM);

            createNotificationInternal(ownerId, NotificationType.CASE_DEADLINE_APPROACHING, priority,
                    title, message, caseId, null, null, null, null, null, null, actionUrl);
        });
    }

    // ============================================
    // TASK NOTIFICATIONS
    // ============================================

    public void notifyTaskAssigned(Long taskId, Long assignedUserId, Long assignedByUserId) {
        taskRepository.findById(taskId).ifPresent(t -> {
            String title = "Task Assigned";
            String message = String.format("You have been assigned to task: %s", t.getTitle());
            String actionUrl = String.format("/tasks/%d", taskId);

            createNotificationInternal(assignedUserId, NotificationType.TASK_ASSIGNED, NotificationPriority.MEDIUM,
                    title, message, t.getLegalCase().getId(), taskId, null, null, null, null, assignedByUserId, actionUrl);
        });
    }

    public void notifyTaskCompleted(Long taskId, Long completedByUserId) {
        taskRepository.findById(taskId).ifPresent(t -> {
            String completedByName = userRepository.findById(completedByUserId).map(User::getFullName).orElse("Someone");
            String title = "Task Completed";
            String message = String.format("%s completed task: %s", completedByName, t.getTitle());
            String actionUrl = String.format("/tasks/%d", taskId);

            // Notify task creator and case owner
            if (t.getCreatedBy() != null) {
                createNotificationInternal(t.getCreatedBy().getId(), NotificationType.TASK_COMPLETED, NotificationPriority.LOW,
                        title, message, t.getLegalCase().getId(), taskId, null, null, null, null, completedByUserId, actionUrl);
            }
            if (t.getLegalCase().getOwner() != null && (t.getCreatedBy() == null || !t.getCreatedBy().getId().equals(t.getLegalCase().getOwner().getId()))) {
                createNotificationInternal(t.getLegalCase().getOwner().getId(), NotificationType.TASK_COMPLETED, NotificationPriority.LOW,
                        title, message, t.getLegalCase().getId(), taskId, null, null, null, null, completedByUserId, actionUrl);
            }
        });
    }

    public void notifyTaskDependencyMet(Long taskId, Long userId) {
        taskRepository.findById(taskId).ifPresent(t -> {
            String title = "Task Unblocked";
            String message = String.format("Task \"%s\" is now ready to work on (dependent task completed)", t.getTitle());
            String actionUrl = String.format("/tasks/%d", taskId);

            createNotificationInternal(userId, NotificationType.TASK_DEPENDENCY_MET, NotificationPriority.MEDIUM,
                    title, message, t.getLegalCase().getId(), taskId, null, null, null, null, null, actionUrl);
        });
    }

    public void notifyTaskDeadlineApproaching(Long taskId, Long assignedUserId, int daysRemaining) {
        taskRepository.findById(taskId).ifPresent(t -> {
            String title = "Task Deadline Approaching";
            String message = String.format("Task \"%s\" is due in %d days", t.getTitle(), daysRemaining);
            String actionUrl = String.format("/tasks/%d", taskId);

            NotificationPriority priority = daysRemaining <= 1 ? NotificationPriority.URGENT :
                    (daysRemaining <= 3 ? NotificationPriority.HIGH : NotificationPriority.MEDIUM);

            createNotificationInternal(assignedUserId, NotificationType.TASK_DEADLINE_APPROACHING, priority,
                    title, message, t.getLegalCase().getId(), taskId, null, null, null, null, null, actionUrl);
        });
    }

    public void notifyTaskOverdue(Long taskId, Long assignedUserId) {
        taskRepository.findById(taskId).ifPresent(t -> {
            String title = "Task Overdue!";
            String message = String.format("Task \"%s\" is past its due date", t.getTitle());
            String actionUrl = String.format("/tasks/%d", taskId);

            createNotificationInternal(assignedUserId, NotificationType.TASK_OVERDUE, NotificationPriority.URGENT,
                    title, message, t.getLegalCase().getId(), taskId, null, null, null, null, null, actionUrl);
        });
    }

    public void notifyTaskMentionedInChat(Long taskId, Long caseId, Long messageId, Long senderId, String messageContent) {
        taskRepository.findById(taskId).ifPresent(t -> {
            if (t.getAssignedTo() != null) {
                String senderName = userRepository.findById(senderId).map(User::getFullName).orElse("Someone");
                String preview = messageContent.length() > 100 ? messageContent.substring(0, 100) + "..." : messageContent;
                String title = "Task Mentioned";
                String message = String.format("%s mentioned task \"%s\" in chat: \"%s\"", senderName, t.getTitle(), preview);
                String actionUrl = String.format("/tasks/%d", taskId);

                createNotificationInternal(t.getAssignedTo().getId(), NotificationType.TASK_MENTIONED, NotificationPriority.MEDIUM,
                        title, message, caseId, taskId, null, messageId, null, null, senderId, actionUrl);
            }
        });
    }

    // ============================================
    // CHAT NOTIFICATIONS
    // ============================================

    public void notifyUserMentionedInChat(Long mentionedUserId, Long caseId, Long messageId, Long senderId, String messageContent) {
        caseRepository.findById(caseId).ifPresent(c -> {
            String senderName = userRepository.findById(senderId).map(User::getFullName).orElse("Someone");
            String preview = messageContent.length() > 100 ? messageContent.substring(0, 100) + "..." : messageContent;
            String title = "You were mentioned";
            String message = String.format("%s mentioned you in case %s: \"%s\"", senderName, c.getTitle(), preview);
            String actionUrl = String.format("/cases/%d/chat", caseId);

            createNotificationInternal(mentionedUserId, NotificationType.USER_MENTIONED, NotificationPriority.HIGH,
                    title, message, caseId, null, null, messageId, null, null, senderId, actionUrl);
        });
    }

    public void notifyNewChatMessage(Long caseId, Long senderId, Long messageId, String messageContent, List<Long> recipientIds) {
        caseRepository.findById(caseId).ifPresent(c -> {
            String senderName = userRepository.findById(senderId).map(User::getFullName).orElse("Someone");
            String preview = messageContent.length() > 100 ? messageContent.substring(0, 100) + "..." : messageContent;
            String title = "New Message";
            String message = String.format("%s sent a message in case %s: \"%s\"", senderName, c.getTitle(), preview);
            String actionUrl = String.format("/cases/%d/chat", caseId);

            for (Long recipientId : recipientIds) {
                if (!recipientId.equals(senderId)) {
                    createNotificationInternal(recipientId, NotificationType.NEW_CHAT_MESSAGE, NotificationPriority.LOW,
                            title, message, caseId, null, null, messageId, null, null, senderId, actionUrl);
                }
            }
        });
    }

    // ============================================
    // COMMENT NOTIFICATIONS
    // ============================================

    public void notifyUserMentionedInComment(Long mentionedUserId, Long commentId, Long caseId, Long actorId) {
        caseRepository.findById(caseId).ifPresent(c -> {
            String actorName = userRepository.findById(actorId).map(User::getFullName).orElse("Someone");
            String title = "You were mentioned in a comment";
            String message = String.format("%s mentioned you in a comment on case %s", actorName, c.getTitle());
            String actionUrl = String.format("/cases/%d/comments", caseId);

            createNotificationInternal(mentionedUserId, NotificationType.USER_MENTIONED_IN_COMMENT, NotificationPriority.HIGH,
                    title, message, caseId, null, commentId, null, null, null, actorId, actionUrl);
        });
    }

    public void notifyCommentReply(Long originalAuthorId, Long commentId, Long caseId, Long actorId) {
        caseRepository.findById(caseId).ifPresent(c -> {
            String actorName = userRepository.findById(actorId).map(User::getFullName).orElse("Someone");
            String title = "New Reply to Your Comment";
            String message = String.format("%s replied to your comment on case %s", actorName, c.getTitle());
            String actionUrl = String.format("/cases/%d/comments", caseId);

            createNotificationInternal(originalAuthorId, NotificationType.COMMENT_REPLY, NotificationPriority.MEDIUM,
                    title, message, caseId, null, commentId, null, null, null, actorId, actionUrl);
        });
    }

    public void notifyNewCaseComment(Long caseId, Long commentId, Long actorId, List<Long> recipientIds) {
        caseRepository.findById(caseId).ifPresent(c -> {
            String actorName = userRepository.findById(actorId).map(User::getFullName).orElse("Someone");
            String title = "New Comment on Case";
            String message = String.format("%s added a comment on case %s", actorName, c.getTitle());
            String actionUrl = String.format("/cases/%d/comments", caseId);

            for (Long recipientId : recipientIds) {
                if (!recipientId.equals(actorId)) {
                    createNotificationInternal(recipientId, NotificationType.NEW_CASE_COMMENT, NotificationPriority.LOW,
                            title, message, caseId, null, commentId, null, null, null, actorId, actionUrl);
                }
            }
        });
    }

    public void notifyNewTaskComment(Long taskId, Long commentId, Long actorId) {
        taskRepository.findById(taskId).ifPresent(t -> {
            String actorName = userRepository.findById(actorId).map(User::getFullName).orElse("Someone");
            String title = "New Comment on Task";
            String message = String.format("%s added a comment on task \"%s\"", actorName, t.getTitle());
            String actionUrl = String.format("/tasks/%d/comments", taskId);

            if (t.getAssignedTo() != null) {
                createNotificationInternal(t.getAssignedTo().getId(), NotificationType.NEW_TASK_COMMENT, NotificationPriority.MEDIUM,
                        title, message, t.getLegalCase().getId(), taskId, commentId, null, null, null, actorId, actionUrl);
            }
        });
    }

    // ============================================
    // DOCUMENT NOTIFICATIONS
    // ============================================

    public void notifyDocumentUploaded(Long documentId, Long caseId, Long taskId, Long uploadedById, List<Long> recipientIds) {
        String documentName = "";
        String actionUrl;
        final Long contextCaseId;  // Make it final
        Long contextTaskId = taskId;

        if (caseId != null) {
            actionUrl = String.format("/cases/%d/documents", caseId);
            contextCaseId = caseId;
        } else if (taskId != null) {
            actionUrl = String.format("/tasks/%d/documents", taskId);
            // Need to fetch the caseId from the task
            final Long[] fetchedCaseId = new Long[1];
            taskRepository.findById(taskId).ifPresent(t -> {
                fetchedCaseId[0] = t.getLegalCase().getId();
            });
            contextCaseId = fetchedCaseId[0];
        } else {
            actionUrl = "/documents";
            contextCaseId = null;
        }

        String uploaderName = userRepository.findById(uploadedById).map(User::getFullName).orElse("Someone");
        String title = "Document Uploaded";
        String message = String.format("%s uploaded a new document", uploaderName);

        final Long finalContextCaseId = contextCaseId;  // Create effectively final variable for lambda
        final Long finalContextTaskId = contextTaskId;

        for (Long recipientId : recipientIds) {
            if (!recipientId.equals(uploadedById)) {
                createNotificationInternal(recipientId, NotificationType.DOCUMENT_UPLOADED, NotificationPriority.LOW,
                        title, message, finalContextCaseId, finalContextTaskId, null, null, documentId, null, uploadedById, actionUrl);
            }
        }
    }

    public void notifyDocumentProcessed(Long documentId, Long uploadedById, boolean success, String errorMessage) {
        String title = success ? "Document Processed" : "Document Processing Failed";
        String message = success ? "Your document has been processed and text extraction is complete" :
                String.format("Document processing failed: %s", errorMessage);
        String actionUrl = String.format("/documents/%d", documentId);

        createNotificationInternal(uploadedById, NotificationType.DOCUMENT_PROCESSED,
                success ? NotificationPriority.LOW : NotificationPriority.MEDIUM,
                title, message, null, null, null, null, documentId, null, null, actionUrl);
    }

    // ============================================
    // AI NOTIFICATIONS
    // ============================================

    public void notifyAIAnalysisComplete(Long interactionId, Long userId, String interactionNumber) {
        String title = "AI Analysis Complete";
        String message = String.format("Your AI analysis (%s) has finished processing", interactionNumber);
        String actionUrl = String.format("/ai/%s", interactionNumber);

        createNotificationInternal(userId, NotificationType.AI_ANALYSIS_COMPLETE, NotificationPriority.LOW,
                title, message, null, null, null, null, null, interactionId, null, actionUrl);
    }

    // ============================================
    // QUERY METHODS
    // ============================================

    public Page<NotificationResponse> getNotificationsForUser(String userIdentifier, int page, int size) {
        Long userId = getUserIdFromIdentifier(userIdentifier);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Notification> notifications = notificationRepository.findByUserIdWithDetails(userId, pageable);
        return notifications.map(NotificationResponse::fromEntity);
    }

    public List<NotificationResponse> getUnreadNotifications(String userIdentifier) {
        Long userId = getUserIdFromIdentifier(userIdentifier);
        List<Notification> notifications = notificationRepository.findUnreadByUserIdWithDetails(userId);
        return notifications.stream()
                .map(NotificationResponse::fromEntity)
                .collect(Collectors.toList());
    }

    public long getUnreadCount(String userIdentifier) {
        Long userId = getUserIdFromIdentifier(userIdentifier);
        return notificationRepository.countByUserIdAndStatus(userId, NotificationStatus.UNREAD);
    }

    public long getUrgentUnreadCount(String userIdentifier) {
        Long userId = getUserIdFromIdentifier(userIdentifier);
        return notificationRepository.countUrgentUnreadByUserId(userId);
    }

    // ============================================
    // UPDATE METHODS
    // ============================================

    public int markAsRead(List<Long> notificationIds, String userIdentifier) {
        Long userId = getUserIdFromIdentifier(userIdentifier);
        int count = notificationRepository.markAsRead(notificationIds, userId, LocalDateTime.now());

        // AUDIT: Notifications marked as read
        recordAudit(com.legalcase.enums.AuditAction.NOTIFICATION_READ,
                com.legalcase.enums.EntityType.NOTIFICATION,
                null,
                null,
                null,
                null,
                "Marked " + count + " notifications as read by " + userIdentifier,
                true,
                null);

        return count;
    }

    public int markAllAsRead(String userIdentifier) {
        Long userId = getUserIdFromIdentifier(userIdentifier);
        int count = notificationRepository.markAllAsRead(userId, LocalDateTime.now());

        // AUDIT: All notifications marked as read
        recordAudit(com.legalcase.enums.AuditAction.NOTIFICATION_READ,
                com.legalcase.enums.EntityType.NOTIFICATION,
                null,
                null,
                null,
                null,
                "Marked all notifications as read by " + userIdentifier,
                true,
                null);

        return count;
    }

    public int archive(List<Long> notificationIds, String userIdentifier) {
        Long userId = getUserIdFromIdentifier(userIdentifier);
        int count = notificationRepository.archive(notificationIds, userId, LocalDateTime.now());

        // AUDIT: Notifications archived
        recordAudit(com.legalcase.enums.AuditAction.NOTIFICATION_DELETE,
                com.legalcase.enums.EntityType.NOTIFICATION,
                null,
                null,
                null,
                null,
                "Archived " + count + " notifications by " + userIdentifier,
                true,
                null);

        return count;
    }

    // ============================================
    // DELETE METHODS
    // ============================================

    public int deleteNotifications(List<Long> notificationIds, String userIdentifier) {
        Long userId = getUserIdFromIdentifier(userIdentifier);
        int count = notificationRepository.deleteByIds(notificationIds, userId);

        // AUDIT: Notifications deleted
        recordAudit(com.legalcase.enums.AuditAction.NOTIFICATION_DELETE,
                com.legalcase.enums.EntityType.NOTIFICATION,
                null,
                null,
                null,
                null,
                "Deleted " + count + " notifications by " + userIdentifier,
                true,
                null);

        return count;
    }

    public int deleteOldReadAndArchivedNotifications() {
        int count = notificationRepository.deleteOldReadAndArchivedNotifications(LocalDateTime.now().minusDays(30));

        // AUDIT: Old notifications cleaned up
        recordAudit(com.legalcase.enums.AuditAction.NOTIFICATION_DELETE,
                com.legalcase.enums.EntityType.NOTIFICATION,
                null,
                null,
                null,
                null,
                "System cleanup: deleted " + count + " old read/archived notifications",
                true,
                null);

        return count;
    }
}