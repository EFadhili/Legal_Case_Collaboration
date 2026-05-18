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

    // ===== EXISTING METHODS =====

    Page<ChatMessage> findByLegalCaseOrderBySentAtDesc(LegalCase legalCase, Pageable pageable);

    List<ChatMessage> findByLegalCaseOrderBySentAtAsc(LegalCase legalCase);

    @Query("SELECT cm FROM ChatMessage cm WHERE cm.legalCase.id = :caseId AND cm.isRead = false AND cm.sender.id != :userId")
    List<ChatMessage> findUnreadMessagesByCaseAndUser(@Param("caseId") Long caseId, @Param("userId") Long userId);

    @Query("SELECT cm FROM ChatMessage cm WHERE cm.legalCase.id IN :caseIds AND cm.isRead = false AND cm.sender.id != :userId")
    List<ChatMessage> findAllUnreadMessagesForUser(@Param("caseIds") List<Long> caseIds, @Param("userId") Long userId);

    @Query("SELECT cm.legalCase.id, COUNT(cm) FROM ChatMessage cm WHERE cm.legalCase.id IN :caseIds AND cm.isRead = false AND cm.sender.id != :userId GROUP BY cm.legalCase.id")
    List<Object[]> countUnreadMessagesByCase(@Param("caseIds") List<Long> caseIds, @Param("userId") Long userId);

    @Modifying
    @Query("UPDATE ChatMessage cm SET cm.isRead = true, cm.readAt = :readAt WHERE cm.id IN :messageIds")
    void markMessagesAsRead(@Param("messageIds") List<Long> messageIds, @Param("readAt") LocalDateTime readAt);

    @Modifying
    @Query("UPDATE ChatMessage cm SET cm.isRead = true, cm.readAt = :readAt WHERE cm.legalCase.id = :caseId AND cm.sender.id != :userId AND cm.isRead = false")
    void markAllMessagesAsReadInCase(@Param("caseId") Long caseId, @Param("userId") Long userId, @Param("readAt") LocalDateTime readAt);

    void deleteByLegalCase(LegalCase legalCase);

    // ===== NEW JOIN FETCH METHODS (Prevent LazyInitializationException) =====

    /**
     * Get messages for a case with all associations initialized (sender, legalCase)
     */
    @Query("SELECT DISTINCT cm FROM ChatMessage cm " +
            "LEFT JOIN FETCH cm.sender " +
            "LEFT JOIN FETCH cm.legalCase c " +
            "LEFT JOIN FETCH c.owner " +
            "WHERE cm.legalCase = :legalCase " +
            "ORDER BY cm.sentAt ASC")
    List<ChatMessage> findByLegalCaseWithDetails(@Param("legalCase") LegalCase legalCase);

    /**
     * Get paginated messages for a case with associations initialized
     */
    @Query("SELECT DISTINCT cm FROM ChatMessage cm " +
            "LEFT JOIN FETCH cm.sender " +
            "LEFT JOIN FETCH cm.legalCase c " +
            "LEFT JOIN FETCH c.owner " +
            "WHERE cm.legalCase = :legalCase " +
            "ORDER BY cm.sentAt DESC")
    Page<ChatMessage> findByLegalCaseWithDetailsPaginated(@Param("legalCase") LegalCase legalCase, Pageable pageable);

    /**
     * Get unread messages with associations initialized
     */
    @Query("SELECT DISTINCT cm FROM ChatMessage cm " +
            "LEFT JOIN FETCH cm.sender " +
            "LEFT JOIN FETCH cm.legalCase c " +
            "LEFT JOIN FETCH c.owner " +
            "WHERE cm.legalCase.id = :caseId AND cm.isRead = false AND cm.sender.id != :userId")
    List<ChatMessage> findUnreadMessagesByCaseAndUserWithDetails(@Param("caseId") Long caseId, @Param("userId") Long userId);
}