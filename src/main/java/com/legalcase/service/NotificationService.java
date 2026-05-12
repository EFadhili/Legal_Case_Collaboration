package com.legalcase.service;

import com.legalcase.dto.response.NotificationResponse;
import com.legalcase.entity.*;
import com.legalcase.enums.NotificationPriority;
import com.legalcase.enums.NotificationStatus;
import com.legalcase.enums.NotificationType;
import com.legalcase.repository.CaseRepository;
import com.legalcase.repository.NotificationRepository;
import com.legalcase.repository.TaskRepository;
import com.legalcase.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

    /**
     * Create a notification.
     */
    public Notification createNotification(Long userId, NotificationType type, NotificationPriority priority,
                                           String title, String message, Long caseId, Long taskId,
                                           Long messageId, Long actorId, String actionUrl) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        Notification notification = new Notification();
        notification.setUser(user);
        notification.setType(type);
        notification.setPriority(priority);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setCaseId(caseId);
        notification.setTaskId(taskId);
        notification.setMessageId(messageId);
        notification.setActorId(actorId);
        notification.setActionUrl(actionUrl);

        // Populate case details if caseId provided
        if (caseId != null) {
            caseRepository.findById(caseId).ifPresent(c -> {
                notification.setCaseNumber(c.getCaseNumber());
                notification.setCaseTitle(c.getTitle());
            });
        }

        // Populate task details if taskId provided
        if (taskId != null) {
            taskRepository.findById(taskId).ifPresent(t -> {
                notification.setTaskTitle(t.getTitle());
            });
        }

        // Populate actor name if actorId provided
        if (actorId != null) {
            userRepository.findById(actorId).ifPresent(actor -> {
                notification.setActorName(actor.getFullName());
            });
        }

        log.info("Created {} notification for user {}: {}", type, userId, title);
        return notificationRepository.save(notification);
    }

    /**
     * Send notification when user is added to a case.
     */
    public void notifyUserAddedToCase(Long userId, Long caseId, Long addedByUserId) {
        caseRepository.findById(caseId).ifPresent(c -> {
            String title = "Added to Case";
            String message = String.format("You have been added to case: %s", c.getTitle());
            String actionUrl = String.format("/cases/%d", caseId);

            createNotification(userId, NotificationType.ADDED_TO_CASE, NotificationPriority.MEDIUM,
                    title, message, caseId, null, null, addedByUserId, actionUrl);
        });
    }

    /**
     * Send notification when task is assigned to a user.
     */
    public void notifyTaskAssigned(Long taskId, Long assignedUserId, Long assignedByUserId) {
        taskRepository.findById(taskId).ifPresent(t -> {
            String title = "Task Assigned";
            String message = String.format("You have been assigned to task: %s", t.getTitle());
            String actionUrl = String.format("/tasks/%d", taskId);

            createNotification(assignedUserId, NotificationType.TASK_ASSIGNED, NotificationPriority.MEDIUM,
                    title, message, t.getLegalCase().getId(), taskId, null, assignedByUserId, actionUrl);
        });
    }

    /**
     * Send notification when user is @mentioned in chat.
     */
    public void notifyUserMentionedInChat(Long mentionedUserId, Long caseId, Long messageId,
                                          Long senderId, String messageContent) {
        caseRepository.findById(caseId).ifPresent(c -> {
            String title = "You were mentioned";
            String preview = messageContent.length() > 100 ? messageContent.substring(0, 100) + "..." : messageContent;
            String message = String.format("%s mentioned you in case %s: \"%s\"",
                    userRepository.findById(senderId).map(User::getFullName).orElse("Someone"),
                    c.getTitle(), preview);
            String actionUrl = String.format("/cases/%d/chat", caseId);

            createNotification(mentionedUserId, NotificationType.USER_MENTIONED, NotificationPriority.HIGH,
                    title, message, caseId, null, messageId, senderId, actionUrl);
        });
    }

    /**
     * Send notification when task is mentioned in chat.
     */
    public void notifyTaskMentionedInChat(Long mentionedTaskId, Long caseId, Long messageId,
                                          Long senderId, String messageContent) {
        taskRepository.findById(mentionedTaskId).ifPresent(t -> {
            String title = "Task Mentioned";
            String preview = messageContent.length() > 100 ? messageContent.substring(0, 100) + "..." : messageContent;
            String message = String.format("%s mentioned task \"%s\" in chat: \"%s\"",
                    userRepository.findById(senderId).map(User::getFullName).orElse("Someone"),
                    t.getTitle(), preview);
            String actionUrl = String.format("/tasks/%d", mentionedTaskId);

            // Notify the task assignee
            if (t.getAssignedTo() != null) {
                createNotification(t.getAssignedTo().getId(), NotificationType.TASK_MENTIONED, NotificationPriority.MEDIUM,
                        title, message, caseId, mentionedTaskId, messageId, senderId, actionUrl);
            }
        });
    }

    /**
     * Send notification when task deadline is approaching.
     */
    public void notifyTaskDeadlineApproaching(Long taskId, Long assignedUserId, int daysRemaining) {
        taskRepository.findById(taskId).ifPresent(t -> {
            String title = "Task Deadline Approaching";
            String message = String.format("Task \"%s\" is due in %d days", t.getTitle(), daysRemaining);
            String actionUrl = String.format("/tasks/%d", taskId);

            NotificationPriority priority = daysRemaining <= 1 ? NotificationPriority.URGENT :
                    (daysRemaining <= 3 ? NotificationPriority.HIGH : NotificationPriority.MEDIUM);

            createNotification(assignedUserId, NotificationType.TASK_DEADLINE_APPROACHING, priority,
                    title, message, t.getLegalCase().getId(), taskId, null, null, actionUrl);
        });
    }

    /**
     * Send notification when task is overdue.
     */
    public void notifyTaskOverdue(Long taskId, Long assignedUserId) {
        taskRepository.findById(taskId).ifPresent(t -> {
            String title = "Task Overdue!";
            String message = String.format("Task \"%s\" is past its due date", t.getTitle());
            String actionUrl = String.format("/tasks/%d", taskId);

            createNotification(assignedUserId, NotificationType.TASK_OVERDUE, NotificationPriority.URGENT,
                    title, message, t.getLegalCase().getId(), taskId, null, null, actionUrl);
        });
    }

    /**
     * Send notification when case deadline is approaching.
     */
    public void notifyCaseDeadlineApproaching(Long caseId, Long ownerId, int daysRemaining) {
        caseRepository.findById(caseId).ifPresent(c -> {
            String title = "Case Deadline Approaching";
            String message = String.format("Case \"%s\" is due in %d days", c.getTitle(), daysRemaining);
            String actionUrl = String.format("/cases/%d", caseId);

            NotificationPriority priority = daysRemaining <= 1 ? NotificationPriority.URGENT :
                    (daysRemaining <= 3 ? NotificationPriority.HIGH : NotificationPriority.MEDIUM);

            createNotification(ownerId, NotificationType.CASE_DEADLINE_APPROACHING, priority,
                    title, message, caseId, null, null, null, actionUrl);
        });
    }

    /**
     * Send notification for new message in case chat (to other members).
     */
    public void notifyNewChatMessage(Long caseId, Long senderId, Long messageId, String messageContent, List<Long> recipientIds) {
        caseRepository.findById(caseId).ifPresent(c -> {
            String senderName = userRepository.findById(senderId).map(User::getFullName).orElse("Someone");
            String preview = messageContent.length() > 100 ? messageContent.substring(0, 100) + "..." : messageContent;
            String title = "New Message";
            String message = String.format("%s sent a message in case %s: \"%s\"", senderName, c.getTitle(), preview);
            String actionUrl = String.format("/cases/%d/chat", caseId);

            for (Long recipientId : recipientIds) {
                // Don't notify the sender
                if (!recipientId.equals(senderId)) {
                    createNotification(recipientId, NotificationType.NEW_CHAT_MESSAGE, NotificationPriority.LOW,
                            title, message, caseId, null, messageId, senderId, actionUrl);
                }
            }
        });
    }

    /**
     * Get notifications for a user (paginated).
     */
    public Page<NotificationResponse> getNotificationsForUser(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Notification> notifications = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        return notifications.map(NotificationResponse::fromEntity);
    }

    /**
     * Get unread notifications for a user.
     */
    public List<NotificationResponse> getUnreadNotifications(Long userId) {
        List<Notification> notifications = notificationRepository.findByUserIdAndStatusOrderByCreatedAtDesc(
                userId, NotificationStatus.UNREAD);
        return notifications.stream()
                .map(NotificationResponse::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Get unread notification count.
     */
    public long getUnreadCount(Long userId) {
        return notificationRepository.countByUserIdAndStatus(userId, NotificationStatus.UNREAD);
    }

    /**
     * Mark notifications as read.
     */
    public int markAsRead(List<Long> notificationIds, Long userId) {
        return notificationRepository.markAsRead(notificationIds, userId, LocalDateTime.now());
    }

    /**
     * Mark all notifications as read for a user.
     */
    public int markAllAsRead(Long userId) {
        return notificationRepository.markAllAsRead(userId, LocalDateTime.now());
    }
}