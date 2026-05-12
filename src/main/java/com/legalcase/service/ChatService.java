package com.legalcase.service;

import com.legalcase.dto.response.UnreadCountResponse;
import com.legalcase.entity.ChatMessage;
import com.legalcase.entity.LegalCase;
import com.legalcase.entity.User;
import com.legalcase.enums.MessageType;
import com.legalcase.repository.CaseMemberRepository;
import com.legalcase.repository.CaseRepository;
import com.legalcase.repository.ChatMessageRepository;
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
    private final NotificationService notificationService;

    /**
     * Verify that a user is a member of a case.
     */
    private void verifyCaseMembership(Long caseId, Long userId) {
        LegalCase legalCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new RuntimeException("Case not found with ID: " + caseId));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + userId));

        if (!caseMemberRepository.existsByLegalCaseAndUser(legalCase, user)) {
            throw new RuntimeException("Access denied. Only case members can participate in this chat.");
        }
    }

    /**
     * Send a new message to a case chat (with mentions support).
     */
    @Transactional
    public ChatMessage sendMessage(String content, MessageType type, Long caseId,
                                   Long senderId, String fileUrl, String fileName,
                                   Long fileSize, List<Long> mentionedUserIds, List<Long> mentionedTaskIds) {
        log.info("User {} sending message to case {}", senderId, caseId);

        // Verify membership
        verifyCaseMembership(caseId, senderId);

        LegalCase legalCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new RuntimeException("Case not found with ID: " + caseId));

        // Check if case is archived - no new messages allowed
        if (legalCase.getStatus() == com.legalcase.enums.CaseStatus.ARCHIVED) {
            throw new RuntimeException("Cannot send messages to archived cases");
        }

        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + senderId));

        ChatMessage message = new ChatMessage();
        message.setContent(content);
        message.setType(type != null ? type : MessageType.TEXT);
        message.setLegalCase(legalCase);
        message.setSender(sender);
        message.setSentAt(LocalDateTime.now());
        message.setRead(false);

        if (fileUrl != null) {
            message.setFileUrl(fileUrl);
            message.setFileName(fileName);
            message.setFileSize(fileSize);
        }

        // Set user mentions
        if (mentionedUserIds != null && !mentionedUserIds.isEmpty()) {
            String mentions = mentionedUserIds.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(","));
            message.setMentionedUserIds(mentions);
            log.info("Message mentions users: {}", mentionedUserIds);
        }

        // Set task mentions
        if (mentionedTaskIds != null && !mentionedTaskIds.isEmpty()) {
            String mentions = mentionedTaskIds.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(","));
            message.setMentionedTaskIds(mentions);
            log.info("Message mentions tasks: {}", mentionedTaskIds);
        }

        ChatMessage saved = chatMessageRepository.save(message);
        log.info("Message sent with ID: {} to case: {}", saved.getId(), caseId);

        // Send notifications for user mentions
        if (mentionedUserIds != null && !mentionedUserIds.isEmpty()) {
            for (Long mentionedUserId : mentionedUserIds) {
                // Don't notify the sender about their own mention
                if (!mentionedUserId.equals(senderId)) {
                    notificationService.notifyUserMentionedInChat(
                            mentionedUserId, caseId, saved.getId(), senderId, content);
                }
            }
        }

        // Send notifications for task mentions
        if (mentionedTaskIds != null && !mentionedTaskIds.isEmpty()) {
            for (Long mentionedTaskId : mentionedTaskIds) {
                notificationService.notifyTaskMentionedInChat(
                        mentionedTaskId, caseId, saved.getId(), senderId, content);
            }
        }

        // Get all case members to notify about new message (excluding sender)
        List<Long> memberIds = caseMemberRepository.findByLegalCase(legalCase).stream()
                .map(cm -> cm.getUser().getId())
                .collect(Collectors.toList());

        if (!memberIds.isEmpty()) {
            notificationService.notifyNewChatMessage(caseId, senderId, saved.getId(), content, memberIds);
        }

        return saved;
    }

    /**
     * Get messages for a case with pagination.
     */
    public Page<ChatMessage> getMessagesByCase(Long caseId, int page, int size, Long userId) {
        log.info("User {} fetching messages for case: {}, page: {}, size: {}", userId, caseId, page, size);

        // Verify membership
        verifyCaseMembership(caseId, userId);

        LegalCase legalCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new RuntimeException("Case not found with ID: " + caseId));

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "sentAt"));
        return chatMessageRepository.findByLegalCaseOrderBySentAtDesc(legalCase, pageable);
    }

    /**
     * Get all messages for a case (for WebSocket initial load).
     */
    public List<ChatMessage> getAllMessagesByCase(Long caseId, Long userId) {
        // Verify membership
        verifyCaseMembership(caseId, userId);

        LegalCase legalCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new RuntimeException("Case not found with ID: " + caseId));
        return chatMessageRepository.findByLegalCaseOrderBySentAtAsc(legalCase);
    }

    /**
     * Get unread messages for a user in a specific case.
     */
    public List<ChatMessage> getUnreadMessagesByCase(Long caseId, Long userId) {
        // Verify membership
        verifyCaseMembership(caseId, userId);

        return chatMessageRepository.findUnreadMessagesByCaseAndUser(caseId, userId);
    }

    /**
     * Get unread counts for a user across all their cases.
     */
    public UnreadCountResponse getUnreadCounts(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + userId));

        // Get all case IDs where user is a member
        List<LegalCase> userCases = caseRepository.findCasesByMemberId(userId);
        List<Long> caseIds = userCases.stream()
                .map(LegalCase::getId)
                .collect(Collectors.toList());

        if (caseIds.isEmpty()) {
            return UnreadCountResponse.builder()
                    .totalUnread(0L)
                    .unreadByCase(new HashMap<>())
                    .build();
        }

        // Count unread by case
        List<Object[]> unreadCounts = chatMessageRepository.countUnreadMessagesByCase(caseIds, userId);
        Map<Long, Long> unreadByCase = new HashMap<>();
        long totalUnread = 0;

        for (Object[] result : unreadCounts) {
            Long caseId = (Long) result[0];
            Long count = (Long) result[1];
            unreadByCase.put(caseId, count);
            totalUnread += count;
        }

        return UnreadCountResponse.builder()
                .totalUnread(totalUnread)
                .unreadByCase(unreadByCase)
                .build();
    }

    /**
     * Mark specific messages as read.
     */
    @Transactional
    public void markMessagesAsRead(List<Long> messageIds, Long userId) {
        log.info("User {} marking {} messages as read", userId, messageIds.size());

        // Optional: Verify user has access to these messages by checking case membership
        if (messageIds != null && !messageIds.isEmpty()) {
            // Get the first message to check case membership
            ChatMessage firstMessage = chatMessageRepository.findById(messageIds.get(0))
                    .orElseThrow(() -> new RuntimeException("Message not found"));
            verifyCaseMembership(firstMessage.getLegalCase().getId(), userId);
        }

        chatMessageRepository.markMessagesAsRead(messageIds, LocalDateTime.now());
    }

    /**
     * Mark all messages in a case as read for a user.
     */
    @Transactional
    public void markAllMessagesAsReadInCase(Long caseId, Long userId) {
        log.info("User {} marking all messages as read in case {}", userId, caseId);

        // Verify membership
        verifyCaseMembership(caseId, userId);

        chatMessageRepository.markAllMessagesAsReadInCase(caseId, userId, LocalDateTime.now());
        log.info("All messages marked as read in case {}", caseId);
    }

    /**
     * Check if a user can access a case chat (public method for WebSocket).
     */
    public boolean canAccessCaseChat(Long caseId, Long userId) {
        try {
            verifyCaseMembership(caseId, userId);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}