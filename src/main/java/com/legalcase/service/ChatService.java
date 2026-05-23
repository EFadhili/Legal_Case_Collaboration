package com.legalcase.service;

import com.legalcase.dto.response.ChatMessageResponse;
import com.legalcase.dto.response.UnreadCountResponse;
import com.legalcase.entity.ChatMessage;
import com.legalcase.entity.LegalCase;
import com.legalcase.entity.User;
import com.legalcase.enums.MessageType;
import com.legalcase.exception.*;
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
                .orElseThrow(() -> new ResourceNotFoundException("Case", caseId));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        if (!caseMemberRepository.existsByLegalCaseAndUser(legalCase, user)) {
            throw new AccessDeniedException("Only case members can participate in this chat.");
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

        verifyCaseMembership(caseId, senderId);

        LegalCase legalCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Case", caseId));

        if (legalCase.getStatus() == com.legalcase.enums.CaseStatus.ARCHIVED) {
            throw new InvalidStatusTransitionException("Cannot send messages to archived cases");
        }

        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new ResourceNotFoundException("User", senderId));

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

        if (mentionedUserIds != null && !mentionedUserIds.isEmpty()) {
            String mentions = mentionedUserIds.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(","));
            message.setMentionedUserIds(mentions);
            log.info("Message mentions users: {}", mentionedUserIds);
        }

        if (mentionedTaskIds != null && !mentionedTaskIds.isEmpty()) {
            String mentions = mentionedTaskIds.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(","));
            message.setMentionedTaskIds(mentions);
            log.info("Message mentions tasks: {}", mentionedTaskIds);
        }

        ChatMessage saved = chatMessageRepository.save(message);
        log.info("Message sent with ID: {} to case: {}", saved.getId(), caseId);

        if (mentionedUserIds != null && !mentionedUserIds.isEmpty()) {
            for (Long mentionedUserId : mentionedUserIds) {
                if (!mentionedUserId.equals(senderId)) {
                    notificationService.notifyUserMentionedInChat(
                            mentionedUserId, caseId, saved.getId(), senderId, content);
                }
            }
        }

        if (mentionedTaskIds != null && !mentionedTaskIds.isEmpty()) {
            for (Long mentionedTaskId : mentionedTaskIds) {
                notificationService.notifyTaskMentionedInChat(
                        mentionedTaskId, caseId, saved.getId(), senderId, content);
            }
        }

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
    public Page<ChatMessageResponse> getMessagesByCase(Long caseId, int page, int size, Long userId) {
        log.info("User {} fetching messages for case: {}, page: {}, size: {}", userId, caseId, page, size);

        verifyCaseMembership(caseId, userId);

        LegalCase legalCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Case", caseId));

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "sentAt"));
        Page<ChatMessage> messages = chatMessageRepository.findByLegalCaseWithDetailsPaginated(legalCase, pageable);

        return messages.map(ChatMessageResponse::fromEntity);
    }

    /**
     * Get all messages for a case (for WebSocket initial load).
     */
    public List<ChatMessageResponse> getAllMessagesByCase(Long caseId, Long userId) {
        verifyCaseMembership(caseId, userId);

        LegalCase legalCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Case", caseId));

        List<ChatMessage> messages = chatMessageRepository.findByLegalCaseWithDetails(legalCase);

        return messages.stream()
                .map(ChatMessageResponse::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Get unread messages for a user in a specific case.
     */
    public List<ChatMessageResponse> getUnreadMessagesByCase(Long caseId, Long userId) {
        verifyCaseMembership(caseId, userId);

        List<ChatMessage> messages = chatMessageRepository.findUnreadMessagesByCaseAndUserWithDetails(caseId, userId);

        return messages.stream()
                .map(ChatMessageResponse::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Get unread counts for a user across all their cases.
     */
    public UnreadCountResponse getUnreadCounts(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

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

        if (messageIds != null && !messageIds.isEmpty()) {
            ChatMessage firstMessage = chatMessageRepository.findById(messageIds.get(0))
                    .orElseThrow(() -> new ResourceNotFoundException("Chat Message", messageIds.get(0)));
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