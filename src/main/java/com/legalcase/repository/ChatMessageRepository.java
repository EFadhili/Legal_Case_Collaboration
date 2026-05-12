package com.legalcase.repository;

import com.legalcase.entity.ChatMessage;
import com.legalcase.entity.LegalCase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    // Get messages for a case with pagination
    Page<ChatMessage> findByLegalCaseOrderBySentAtDesc(LegalCase legalCase, Pageable pageable);

    // Get messages for a case (latest first for WebSocket initial load)
    List<ChatMessage> findByLegalCaseOrderBySentAtAsc(LegalCase legalCase);

    // Get unread messages for a user in a specific case
    @Query("SELECT cm FROM ChatMessage cm WHERE cm.legalCase.id = :caseId AND cm.isRead = false AND cm.sender.id != :userId")
    List<ChatMessage> findUnreadMessagesByCaseAndUser(@Param("caseId") Long caseId, @Param("userId") Long userId);

    // Get all unread messages for a user across all cases they are members of
    @Query("SELECT cm FROM ChatMessage cm WHERE cm.legalCase.id IN :caseIds AND cm.isRead = false AND cm.sender.id != :userId")
    List<ChatMessage> findAllUnreadMessagesForUser(@Param("caseIds") List<Long> caseIds, @Param("userId") Long userId);

    // Count unread messages by case for a user
    @Query("SELECT cm.legalCase.id, COUNT(cm) FROM ChatMessage cm WHERE cm.legalCase.id IN :caseIds AND cm.isRead = false AND cm.sender.id != :userId GROUP BY cm.legalCase.id")
    List<Object[]> countUnreadMessagesByCase(@Param("caseIds") List<Long> caseIds, @Param("userId") Long userId);

    // Mark messages as read
    @Modifying
    @Query("UPDATE ChatMessage cm SET cm.isRead = true, cm.readAt = :readAt WHERE cm.id IN :messageIds")
    void markMessagesAsRead(@Param("messageIds") List<Long> messageIds, @Param("readAt") LocalDateTime readAt);

    // Mark all messages in a case as read for a user (excluding user's own messages)
    @Modifying
    @Query("UPDATE ChatMessage cm SET cm.isRead = true, cm.readAt = :readAt WHERE cm.legalCase.id = :caseId AND cm.sender.id != :userId AND cm.isRead = false")
    void markAllMessagesAsReadInCase(@Param("caseId") Long caseId, @Param("userId") Long userId, @Param("readAt") LocalDateTime readAt);

    // Delete all messages for a case (when case is archived/deleted)
    void deleteByLegalCase(LegalCase legalCase);
}