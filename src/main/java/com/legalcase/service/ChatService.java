package com.legalcase.service;

import com.legalcase.dto.response.*;
import com.legalcase.entity.ChatMessage;
import com.legalcase.entity.LegalCase;
import com.legalcase.entity.Task;
import com.legalcase.entity.User;
import com.legalcase.enums.MessageType;
import com.legalcase.enums.Role;
import com.legalcase.exception.*;
import com.legalcase.repository.CaseMemberRepository;
import com.legalcase.repository.CaseRepository;
import com.legalcase.repository.ChatMessageRepository;
import com.legalcase.repository.TaskRepository;
import com.legalcase.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ChatService {

    private final ChatMessageRepository chatMessageRepository;
    private final CaseRepository caseRepository;
    private final UserRepository userRepository;
    private final CaseMemberRepository caseMemberRepository;
    private final TaskRepository taskRepository;
    private final NotificationService notificationService;
    private final ApplicationEventPublisher eventPublisher;

    // ============================================
    // HELPER METHODS
    // ============================================

    private static class CachedCaseInfo {
        final LegalCase legalCase;
        final User user;

        CachedCaseInfo(LegalCase legalCase, User user) {
            this.legalCase = legalCase;
            this.user = user;
        }
    }

    private final ThreadLocal<CachedCaseInfo> caseInfoCache = new ThreadLocal<>();

    private LegalCase findCaseWithCache(String caseIdentifier, String userIdentifier) {
        CachedCaseInfo cached = caseInfoCache.get();
        if (cached != null) {
            return cached.legalCase;
        }

        LegalCase legalCase = findCase(caseIdentifier);

        if (userIdentifier != null) {
            User user = findUserByIdentifier(userIdentifier);
            caseInfoCache.set(new CachedCaseInfo(legalCase, user));
        }

        return legalCase;
    }

    private void clearCache() {
        caseInfoCache.remove();
    }

    private LegalCase findCase(String caseIdentifier) {
        if (caseIdentifier == null || caseIdentifier.trim().isEmpty()) {
            throw new IllegalArgumentException("Case identifier is required");
        }
        try {
            Long id = Long.parseLong(caseIdentifier);
            return caseRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Case", id));
        } catch (NumberFormatException e) {
            return caseRepository.findByCaseNumberWithDetails(caseIdentifier)
                    .orElseThrow(() -> new ResourceNotFoundException("Case", "caseNumber", caseIdentifier));
        }
    }

    private User findUserByIdentifier(String userIdentifier) {
        return userRepository.findByUsername(userIdentifier)
                .or(() -> userRepository.findByEmail(userIdentifier))
                .orElseThrow(() -> new ResourceNotFoundException("User", "username or email", userIdentifier));
    }

    private Task findTaskByIdentifier(String taskIdentifier) {
        if (taskIdentifier == null || taskIdentifier.trim().isEmpty()) {
            throw new IllegalArgumentException("Task identifier is required");
        }
        try {
            Long id = Long.parseLong(taskIdentifier);
            return taskRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Task", id));
        } catch (NumberFormatException e) {
            return taskRepository.findByTaskNumber(taskIdentifier)
                    .orElseThrow(() -> new ResourceNotFoundException("Task", "taskNumber", taskIdentifier));
        }
    }

    public Long getUserIdFromIdentifier(String userIdentifier) {
        return findUserByIdentifier(userIdentifier).getId();
    }

    public Long getCaseIdFromIdentifier(String caseIdentifier) {
        return findCase(caseIdentifier).getId();
    }

    private void verifyCaseMembership(String caseIdentifier, String userIdentifier) {
        CachedCaseInfo cached = caseInfoCache.get();
        if (cached != null) {
            LegalCase legalCase = cached.legalCase;
            User user = cached.user;
            if (!caseMemberRepository.existsByLegalCaseAndUser(legalCase, user)) {
                throw new AccessDeniedException("Only case members can participate in this chat.");
            }
            return;
        }

        LegalCase legalCase = findCase(caseIdentifier);
        User user = findUserByIdentifier(userIdentifier);

        if (!caseMemberRepository.existsByLegalCaseAndUser(legalCase, user)) {
            throw new AccessDeniedException("Only case members can participate in this chat.");
        }
    }

    private void verifyCaseMembership(Long caseId, Long userId) {
        LegalCase legalCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Case", caseId));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        if (!caseMemberRepository.existsByLegalCaseAndUser(legalCase, user)) {
            throw new AccessDeniedException("Only case members can participate in this chat.");
        }
    }

    // ============================================
    // SEND MESSAGE
    // ============================================

    @Transactional
    public ChatMessage sendMessage(String content, MessageType type, String caseIdentifier,
                                   String senderIdentifier, String fileUrl, String fileName,
                                   Long fileSize, List<String> mentionedUserIdentifiers,
                                   List<String> mentionedTaskIdentifiers) {

        try {
            LegalCase legalCase = findCaseWithCache(caseIdentifier, senderIdentifier);
            User sender = caseInfoCache.get() != null ? caseInfoCache.get().user : findUserByIdentifier(senderIdentifier);
            Long senderId = sender.getId();

            log.info("User {} sending message to case {}", senderIdentifier, caseIdentifier);

            if (caseInfoCache.get() == null) {
                verifyCaseMembership(legalCase.getId(), senderId);
            } else {
                CachedCaseInfo cached = caseInfoCache.get();
                if (!caseMemberRepository.existsByLegalCaseAndUser(cached.legalCase, cached.user)) {
                    throw new AccessDeniedException("Only case members can participate in this chat.");
                }
            }

            if (legalCase.getStatus() == com.legalcase.enums.CaseStatus.ARCHIVED) {
                throw new InvalidStatusTransitionException("Cannot send messages to archived cases");
            }

            ChatMessage message = new ChatMessage();
            message.setContent(content);
            message.setType(type != null ? type : MessageType.TEXT);
            message.setLegalCase(legalCase);
            message.setSender(sender);
            message.setSentAt(LocalDateTime.now());
            message.setRead(false);
            message.setDeleted(false); // Initialize as not deleted

            if (fileUrl != null) {
                log.debug("File attachment: {} ({})", fileName, fileUrl);
                message.setFileUrl(fileUrl);
                message.setFileName(fileName);
                message.setFileSize(fileSize);
            }

            // Process user mentions
            if (mentionedUserIdentifiers != null && !mentionedUserIdentifiers.isEmpty()) {
                List<Long> mentionedUserIds = new ArrayList<>();
                for (String userIdentifier : mentionedUserIdentifiers) {
                    try {
                        User mentionedUser = findUserByIdentifier(userIdentifier);
                        mentionedUserIds.add(mentionedUser.getId());
                    } catch (ResourceNotFoundException e) {
                        log.warn("Mentioned user not found: {}", userIdentifier);
                    }
                }
                if (!mentionedUserIds.isEmpty()) {
                    String mentions = mentionedUserIds.stream()
                            .map(String::valueOf)
                            .collect(Collectors.joining(","));
                    message.setMentionedUserIds(mentions);
                    log.info("Message mentions users: {}", mentionedUserIds);
                }
            }

            // Process task mentions
            if (mentionedTaskIdentifiers != null && !mentionedTaskIdentifiers.isEmpty()) {
                List<Long> mentionedTaskIds = new ArrayList<>();
                for (String taskIdentifier : mentionedTaskIdentifiers) {
                    try {
                        Task mentionedTask = findTaskByIdentifier(taskIdentifier);
                        mentionedTaskIds.add(mentionedTask.getId());
                        log.debug("Task mention resolved: {} -> ID {}", taskIdentifier, mentionedTask.getId());
                    } catch (ResourceNotFoundException e) {
                        log.warn("Mentioned task not found: {}", taskIdentifier);
                    } catch (Exception e) {
                        log.warn("Invalid task mention: {}", taskIdentifier, e);
                    }
                }
                if (!mentionedTaskIds.isEmpty()) {
                    String mentions = mentionedTaskIds.stream()
                            .map(String::valueOf)
                            .collect(Collectors.joining(","));
                    message.setMentionedTaskIds(mentions);
                    log.info("Message mentions tasks: {}", mentionedTaskIds);
                }
            }

            ChatMessage saved = chatMessageRepository.save(message);
            log.info("Message sent with ID: {} to case: {}", saved.getId(), caseIdentifier);

            // Get all case members for notifications
            List<Long> memberIds = caseMemberRepository.findByLegalCase(legalCase).stream()
                    .map(cm -> cm.getUser().getId())
                    .filter(id -> !id.equals(senderId))
                    .collect(Collectors.toList());

            // Send notifications for mentioned users
            if (message.getMentionedUserIdsAsList() != null) {
                for (Long mentionedUserId : message.getMentionedUserIdsAsList()) {
                    if (!mentionedUserId.equals(senderId)) {
                        notificationService.notifyUserMentionedInChat(
                                mentionedUserId, legalCase.getId(), saved.getId(), senderId, content);
                    }
                }
            }

            // Send notifications for mentioned tasks
            if (message.getMentionedTaskIdsAsList() != null) {
                for (Long mentionedTaskId : message.getMentionedTaskIdsAsList()) {
                    notificationService.notifyTaskMentionedInChat(
                            mentionedTaskId, legalCase.getId(), saved.getId(), senderId, content);
                }
            }

            // Notify all case members about new message
            if (!memberIds.isEmpty()) {
                notificationService.notifyNewChatMessage(legalCase.getId(), senderId, saved.getId(), content, memberIds);
            }

            return saved;
        } finally {
            clearCache();
        }
    }

    // ============================================
    // DELETE MESSAGE (NEW)
    // ============================================

    @Transactional
    public MessageDeletedResponse deleteMessage(Long messageId, String userIdentifier, String reason) {
        log.info("User {} attempting to delete message {}", userIdentifier, messageId);

        User user = findUserByIdentifier(userIdentifier);
        ChatMessage message = chatMessageRepository.findByIdAndIsDeletedFalse(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message", messageId));

        // Verify user is a case member
        verifyCaseMembership(message.getLegalCase().getId(), user.getId());

        LocalDateTime now = LocalDateTime.now();
        boolean isAdmin = user.getRole() == Role.ADMIN;
        boolean isLawyer = user.getRole() == Role.LAWYER;
        boolean isSender = message.getSender().getId().equals(user.getId());
        boolean isWithinTimeLimit = message.getSentAt().isAfter(now.minusMinutes(5));

        // Permission logic
        if (isAdmin) {
            log.info("Admin {} deleted message {} from case {}",
                    userIdentifier, messageId, message.getLegalCase().getCaseNumber());
        }
        else if (isLawyer) {
            log.info("Lawyer {} deleted message {} from case {}",
                    userIdentifier, messageId, message.getLegalCase().getCaseNumber());
        }
        else if (isSender && isWithinTimeLimit) {
            log.info("User {} deleted their own message {} from case {}",
                    userIdentifier, messageId, message.getLegalCase().getCaseNumber());
        }
        else if (isSender && !isWithinTimeLimit) {
            throw new AccessDeniedException("You can only delete messages within 5 minutes of sending");
        }
        else {
            throw new AccessDeniedException("You don't have permission to delete this message");
        }

        // Perform soft delete
        String deleteReason = (reason != null && !reason.isEmpty()) ? reason : "No reason provided";
        chatMessageRepository.softDeleteMessage(messageId, now, user.getId(), deleteReason);

        // Notify case members about deletion (optional)
        // notificationService.notifyMessageDeleted(message.getLegalCase().getId(), messageId, user.getId());

        return MessageDeletedResponse.builder()
                .messageId(messageId)
                .caseId(message.getLegalCase().getId())
                .caseNumber(message.getLegalCase().getCaseNumber())
                .deletedBy(user.getId())
                .deletedByName(user.getFullName())
                .deletedAt(now)
                .reason(deleteReason)
                .isDeleted(true)
                .build();
    }

    // ============================================
    // GET MESSAGES
    // ============================================

    public Page<ChatMessageResponse> getMessagesByCase(String caseIdentifier, int page, int size, String userIdentifier) {
        log.info("User {} fetching messages for case: {}, page: {}, size: {}",
                userIdentifier, caseIdentifier, page, size);

        verifyCaseMembership(caseIdentifier, userIdentifier);

        LegalCase legalCase = findCase(caseIdentifier);

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "sentAt"));
        Page<ChatMessage> messages = chatMessageRepository.findByLegalCaseWithDetailsPaginated(legalCase, pageable);

        return messages.map(ChatMessageResponse::fromEntity);
    }

    public List<ChatMessageResponse> getAllMessagesByCase(String caseIdentifier, String userIdentifier) {
        verifyCaseMembership(caseIdentifier, userIdentifier);

        LegalCase legalCase = findCase(caseIdentifier);

        List<ChatMessage> messages = chatMessageRepository.findByLegalCaseForChat(legalCase);

        return messages.stream()
                .map(ChatMessageResponse::fromEntity)
                .collect(Collectors.toList());
    }

    public List<ChatMessageResponse> getUnreadMessagesByCase(String caseIdentifier, String userIdentifier) {
        log.info("Getting unread messages for user {} in case {}", userIdentifier, caseIdentifier);

        verifyCaseMembership(caseIdentifier, userIdentifier);

        Long caseId = getCaseIdFromIdentifier(caseIdentifier);
        Long userId = getUserIdFromIdentifier(userIdentifier);

        List<ChatMessage> messages = chatMessageRepository.findUnreadMessagesByCaseAndUserWithDetails(caseId, userId);

        log.info("Found {} unread messages for user {} in case {}", messages.size(), userIdentifier, caseIdentifier);

        return messages.stream()
                .map(ChatMessageResponse::fromEntity)
                .collect(Collectors.toList());
    }

    // ============================================
    // ENHANCED UNREAD STATUS (NEW)
    // ============================================

    public CaseUnreadStatusResponse getDetailedUnreadStatus(String userIdentifier) {
        User user = findUserByIdentifier(userIdentifier);
        Long userId = user.getId();

        log.info("Getting detailed unread status for user: {} (ID: {})", userIdentifier, userId);

        List<LegalCase> userCases = caseRepository.findCasesByMemberId(userId);

        List<CaseUnreadInfo> casesWithUnread = new ArrayList<>();
        List<CaseUnreadInfo> allCases = new ArrayList<>();
        long totalUnread = 0;

        for (LegalCase legalCase : userCases) {
            Long caseId = legalCase.getId();
            long unreadCount = chatMessageRepository.countUnreadMessagesInCase(caseId, userId);
            totalUnread += unreadCount;

            // Get last message info
            ChatMessage lastMessage = chatMessageRepository.findLatestMessageInCase(legalCase).orElse(null);

            CaseUnreadInfo info = CaseUnreadInfo.builder()
                    .caseId(caseId)
                    .caseNumber(legalCase.getCaseNumber())
                    .caseTitle(legalCase.getTitle())
                    .unreadCount(unreadCount)
                    .lastMessageAt(lastMessage != null ? lastMessage.getSentAt() : null)
                    .lastMessagePreview(lastMessage != null ?
                            truncate(lastMessage.getContent(), 50) : null)
                    .lastMessageSenderId(lastMessage != null ?
                            lastMessage.getSender().getId() : null)
                    .lastMessageSenderName(lastMessage != null ?
                            lastMessage.getSender().getFullName() : null)
                    .build();

            allCases.add(info);

            if (unreadCount > 0) {
                casesWithUnread.add(info);
            }
        }

        // Sort cases with unread first, then by last message time descending
        allCases.sort((a, b) -> {
            if (a.getUnreadCount() > 0 && b.getUnreadCount() == 0) return -1;
            if (a.getUnreadCount() == 0 && b.getUnreadCount() > 0) return 1;
            if (a.getLastMessageAt() == null) return 1;
            if (b.getLastMessageAt() == null) return -1;
            return b.getLastMessageAt().compareTo(a.getLastMessageAt());
        });

        log.info("User {} has {} total unread messages across {} cases with unread",
                userIdentifier, totalUnread, casesWithUnread.size());

        return CaseUnreadStatusResponse.builder()
                .totalUnread(totalUnread)
                .casesWithUnread(casesWithUnread)
                .allCases(allCases)
                .build();
    }

    private String truncate(String text, int length) {
        if (text == null) return "";
        return text.length() <= length ? text : text.substring(0, length) + "...";
    }

    public UnreadCountResponse getUnreadCounts(String userIdentifier) {
        User user = findUserByIdentifier(userIdentifier);
        Long userId = user.getId();

        log.info("Getting unread counts for user: {} (ID: {})", userIdentifier, userId);

        List<LegalCase> userCases = caseRepository.findCasesByMemberId(userId);
        List<Long> caseIds = userCases.stream()
                .map(LegalCase::getId)
                .collect(Collectors.toList());

        if (caseIds.isEmpty()) {
            log.info("User {} is not a member of any cases", userIdentifier);
            return UnreadCountResponse.builder()
                    .totalUnread(0L)
                    .unreadByCase(new HashMap<>())
                    .build();
        }

        List<Object[]> unreadCounts = chatMessageRepository.countUnreadMessagesByCase(caseIds, userId);
        Map<Long, Long> unreadByCase = new HashMap<>();
        long totalUnread = 0;

        for (Object[] result : unreadCounts) {
            Long caseId = (Long) result[0];
            Long count = (Long) result[1];
            unreadByCase.put(caseId, count);
            totalUnread += count;
            log.debug("Case {} has {} unread messages from other users for user {}", caseId, count, userIdentifier);
        }

        for (Long caseId : caseIds) {
            if (!unreadByCase.containsKey(caseId)) {
                unreadByCase.put(caseId, 0L);
            }
        }

        log.info("Total unread messages from other users for {}: {}", userIdentifier, totalUnread);

        return UnreadCountResponse.builder()
                .totalUnread(totalUnread)
                .unreadByCase(unreadByCase)
                .build();
    }

    // ============================================
    // MARK MESSAGES AS READ
    // ============================================

    @Transactional
    public void markMessagesAsRead(List<Long> messageIds, String userIdentifier) {
        Long userId = getUserIdFromIdentifier(userIdentifier);
        log.info("User {} marking {} messages as read", userIdentifier, messageIds.size());

        if (messageIds == null || messageIds.isEmpty()) {
            log.warn("Empty message list provided for mark as read");
            return;
        }

        List<ChatMessage> messages = chatMessageRepository.findByIdsWithCase(messageIds);

        if (messages.size() != messageIds.size()) {
            List<Long> foundIds = messages.stream().map(ChatMessage::getId).collect(Collectors.toList());
            List<Long> missingIds = messageIds.stream()
                    .filter(id -> !foundIds.contains(id))
                    .collect(Collectors.toList());
            throw new ResourceNotFoundException("Chat Messages not found: " + missingIds);
        }

        Long caseId = messages.get(0).getLegalCase().getId();
        boolean allSameCase = messages.stream()
                .allMatch(m -> m.getLegalCase().getId().equals(caseId));

        if (!allSameCase) {
            throw new IllegalArgumentException("All messages must belong to the same case");
        }

        verifyCaseMembership(caseId, userId);

        chatMessageRepository.markMessagesAsRead(messageIds, LocalDateTime.now());
        log.info("Marked {} messages as read for user {} in case {}", messageIds.size(), userIdentifier, caseId);
    }

    @Transactional
    public void markAllMessagesAsReadInCase(String caseIdentifier, String userIdentifier) {
        log.info("User {} marking all messages as read in case {}", userIdentifier, caseIdentifier);

        verifyCaseMembership(caseIdentifier, userIdentifier);

        Long caseId = getCaseIdFromIdentifier(caseIdentifier);
        Long userId = getUserIdFromIdentifier(userIdentifier);

        chatMessageRepository.markAllMessagesAsReadInCase(caseId, userId, LocalDateTime.now());
        log.info("All messages marked as read in case {}", caseIdentifier);
    }

    // ============================================
    // WEBSOCKET ACCESS CHECK
    // ============================================

    public boolean canAccessCaseChat(String caseIdentifier, String userIdentifier) {
        try {
            verifyCaseMembership(caseIdentifier, userIdentifier);
            return true;
        } catch (Exception e) {
            log.warn("Access denied for user {} to case {}: {}", userIdentifier, caseIdentifier, e.getMessage());
            return false;
        }
    }

    public void validateWebSocketSession(String sessionCaseIdentifier, String requestedCaseIdentifier, String userIdentifier) {
        if (sessionCaseIdentifier != null && !sessionCaseIdentifier.equals(requestedCaseIdentifier)) {
            log.warn("User {} attempted to send to case {} but joined case {}",
                    userIdentifier, requestedCaseIdentifier, sessionCaseIdentifier);
            throw new AccessDeniedException("Cannot send to different case than joined. You joined case: " + sessionCaseIdentifier);
        }

        verifyCaseMembership(requestedCaseIdentifier, userIdentifier);
    }

    @Transactional
    public MessageEditedResponse editMessage(Long messageId, String newContent,
                                             String userIdentifier, String reason) {
        log.info("User {} attempting to edit message {}", userIdentifier, messageId);

        User user = findUserByIdentifier(userIdentifier);
        ChatMessage message = chatMessageRepository.findByIdAndIsDeletedFalse(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message", messageId));

        // Verify user is a case member
        verifyCaseMembership(message.getLegalCase().getId(), user.getId());

        LocalDateTime now = LocalDateTime.now();
        boolean isAdmin = user.getRole() == Role.ADMIN;
        boolean isLawyer = user.getRole() == Role.LAWYER;
        boolean isSender = message.getSender().getId().equals(user.getId());
        boolean isWithinTimeLimit = message.getSentAt().isAfter(now.minusMinutes(10)); // 10 min edit window

        // Permission logic (stricter than delete)
        if (isAdmin) {
            log.info("Admin {} editing message {}", userIdentifier, messageId);
        }
        else if (isLawyer) {
            // Lawyers can edit any message in their cases (for corrections)
            log.info("Lawyer {} editing message {}", userIdentifier, messageId);
        }
        else if (isSender && isWithinTimeLimit) {
            log.info("User {} editing their own message within time limit", userIdentifier);
        }
        else if (isSender && !isWithinTimeLimit) {
            throw new AccessDeniedException("You can only edit messages within 10 minutes of sending");
        }
        else {
            throw new AccessDeniedException("You don't have permission to edit this message");
        }

        // Create edit history record (JSON format)
        String editRecord = String.format(
                "{\"timestamp\":\"%s\",\"editedBy\":%d,\"editedByName\":\"%s\",\"oldContent\":\"%s\",\"reason\":\"%s\"}|",
                now.toString(), user.getId(), user.getFullName(),
                escapeJson(message.getContent()),
                reason != null ? escapeJson(reason) : ""
        );

        // Update message
        chatMessageRepository.updateMessageContent(
                messageId, newContent, now, user.getId(),user.getFullName(), editRecord
        );

        return MessageEditedResponse.builder()
                .messageId(messageId)
                .caseId(message.getLegalCase().getId())
                .caseNumber(message.getLegalCase().getCaseNumber())
                .oldContent(message.getContent())
                .newContent(newContent)
                .editedBy(user.getId())
                .editedByName(user.getFullName())
                .editedAt(now)
                .reason(reason)
                .isEdited(true)
                .build();
    }

    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}