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
import java.util.Optional;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    // ============================================
    // OVERRIDE DEFAULT JPA METHODS TO EXCLUDE DELETED
    // ============================================

    @Override
    @Query("SELECT cm FROM ChatMessage cm WHERE cm.id = :id AND cm.isDeleted = false")
    Optional<ChatMessage> findById(@Param("id") Long id);

    @Override
    @Query("SELECT cm FROM ChatMessage cm WHERE cm.isDeleted = false")
    List<ChatMessage> findAll();

    @Override
    @Query("SELECT cm FROM ChatMessage cm WHERE cm.id IN :ids AND cm.isDeleted = false")
    List<ChatMessage> findAllById(@Param("ids") Iterable<Long> ids);

    // ============================================
    // BASIC METHODS
    // ============================================

    void deleteByLegalCase(LegalCase legalCase);

    // ============================================
    // GET MESSAGES (excluding deleted)
    // ============================================

    @Query("SELECT DISTINCT cm FROM ChatMessage cm " +
            "LEFT JOIN FETCH cm.sender " +
            "LEFT JOIN FETCH cm.legalCase c " +
            "WHERE cm.legalCase = :legalCase " +
            "AND cm.isDeleted = false " +
            "ORDER BY cm.sentAt ASC")
    List<ChatMessage> findByLegalCaseForChat(@Param("legalCase") LegalCase legalCase);

    @Query("SELECT DISTINCT cm FROM ChatMessage cm " +
            "LEFT JOIN FETCH cm.sender " +
            "LEFT JOIN FETCH cm.legalCase c " +
            "LEFT JOIN FETCH c.owner " +
            "WHERE cm.legalCase = :legalCase " +
            "AND cm.isDeleted = false " +
            "ORDER BY cm.sentAt DESC")
    Page<ChatMessage> findByLegalCaseWithDetailsPaginated(@Param("legalCase") LegalCase legalCase, Pageable pageable);

    // ============================================
    // UNREAD MESSAGES (excluding deleted)
    // ============================================

    @Query("SELECT DISTINCT cm FROM ChatMessage cm " +
            "LEFT JOIN FETCH cm.sender " +
            "LEFT JOIN FETCH cm.legalCase c " +
            "LEFT JOIN FETCH c.owner " +
            "WHERE cm.legalCase.id = :caseId " +
            "AND cm.sender.id != :userId " +
            "AND cm.isRead = false " +
            "AND cm.isDeleted = false " +
            "ORDER BY cm.sentAt ASC")
    List<ChatMessage> findUnreadMessagesByCaseAndUserWithDetails(@Param("caseId") Long caseId,
                                                                 @Param("userId") Long userId);

    @Query("SELECT cm.legalCase.id, COUNT(cm) FROM ChatMessage cm " +
            "WHERE cm.legalCase.id IN :caseIds " +
            "AND cm.sender.id != :userId " +
            "AND cm.isRead = false " +
            "AND cm.isDeleted = false " +
            "GROUP BY cm.legalCase.id")
    List<Object[]> countUnreadMessagesByCase(@Param("caseIds") List<Long> caseIds,
                                             @Param("userId") Long userId);

    @Query("SELECT COUNT(cm) FROM ChatMessage cm " +
            "WHERE cm.legalCase.id = :caseId " +
            "AND cm.sender.id != :userId " +
            "AND cm.isRead = false " +
            "AND cm.isDeleted = false")
    long countUnreadMessagesInCase(@Param("caseId") Long caseId,
                                   @Param("userId") Long userId);

    // ============================================
    // LATEST MESSAGE (excluding deleted)
    // ============================================

    @Query("SELECT cm FROM ChatMessage cm " +
            "LEFT JOIN FETCH cm.sender " +
            "WHERE cm.legalCase = :legalCase " +
            "AND cm.isDeleted = false " +
            "ORDER BY cm.sentAt DESC")
    Optional<ChatMessage> findLatestMessageInCase(@Param("legalCase") LegalCase legalCase);

    Optional<ChatMessage> findTopByLegalCaseAndIsDeletedFalseOrderBySentAtDesc(LegalCase legalCase);

    // ============================================
    // MARK MESSAGES AS READ (only non-deleted)
    // ============================================

    @Modifying
    @Query("UPDATE ChatMessage cm SET cm.isRead = true, cm.readAt = :readAt " +
            "WHERE cm.id IN :messageIds AND cm.isDeleted = false")
    void markMessagesAsRead(@Param("messageIds") List<Long> messageIds,
                            @Param("readAt") LocalDateTime readAt);

    @Modifying
    @Query("UPDATE ChatMessage cm SET cm.isRead = true, cm.readAt = :readAt " +
            "WHERE cm.legalCase.id = :caseId " +
            "AND cm.sender.id != :userId " +
            "AND cm.isRead = false " +
            "AND cm.isDeleted = false")
    void markAllMessagesAsReadInCase(@Param("caseId") Long caseId,
                                     @Param("userId") Long userId,
                                     @Param("readAt") LocalDateTime readAt);

    // ============================================
    // DELETE SUPPORT
    // ============================================

    @Modifying
    @Query("UPDATE ChatMessage cm SET cm.isDeleted = true, " +
            "cm.deletedAt = :deletedAt, " +
            "cm.deletedBy = :deletedBy, " +
            "cm.deletedReason = :deletedReason " +
            "WHERE cm.id = :messageId")
    void softDeleteMessage(@Param("messageId") Long messageId,
                           @Param("deletedAt") LocalDateTime deletedAt,
                           @Param("deletedBy") Long deletedBy,
                           @Param("deletedReason") String deletedReason);

    Optional<ChatMessage> findByIdAndIsDeletedFalse(Long id);

    // ============================================
    // UTILITY METHODS (excluding deleted)
    // ============================================

    @Query("SELECT DISTINCT cm FROM ChatMessage cm " +
            "LEFT JOIN FETCH cm.legalCase " +
            "WHERE cm.id IN :messageIds AND cm.isDeleted = false")
    List<ChatMessage> findByIdsWithCase(@Param("messageIds") List<Long> messageIds);

    @Query("SELECT cm.id, cm.isRead, cm.sentAt, cm.sender.id FROM ChatMessage cm " +
            "WHERE cm.legalCase.id = :caseId AND cm.isDeleted = false " +
            "ORDER BY cm.sentAt DESC")
    List<Object[]> debugMessageReadStatus(@Param("caseId") Long caseId);

    // ============================================
    // ADMIN METHODS (to view deleted messages if needed)
    // ============================================

    @Query("SELECT DISTINCT cm FROM ChatMessage cm " +
            "LEFT JOIN FETCH cm.sender " +
            "LEFT JOIN FETCH cm.legalCase c " +
            "WHERE cm.legalCase = :legalCase " +
            "AND cm.isDeleted = true " +
            "ORDER BY cm.deletedAt DESC")
    List<ChatMessage> findDeletedMessagesByCase(@Param("legalCase") LegalCase legalCase);

    @Modifying
    @Query("UPDATE ChatMessage cm SET " +
            "cm.content = :newContent, " +
            "cm.isEdited = true, " +
            "cm.editedAt = :editedAt, " +
            "cm.editedBy = :editedBy, " +
            "cm.editedByName = :editedByName, " +
            "cm.originalContent = CASE WHEN cm.originalContent IS NULL THEN cm.content ELSE cm.originalContent END, " +
            "cm.editHistory = FUNCTION('CONCAT', COALESCE(cm.editHistory, ''), :editRecord) " +
            "WHERE cm.id = :messageId AND cm.isDeleted = false")
    void updateMessageContent(@Param("messageId") Long messageId,
                              @Param("newContent") String newContent,
                              @Param("editedAt") LocalDateTime editedAt,
                              @Param("editedBy") Long editedBy,
                              @Param("editedByName") String editedByName,
                              @Param("editRecord") String editRecord);

    // Optional: Get edit history
    @Query("SELECT cm.originalContent, cm.editHistory, cm.editedAt, cm.editedBy " +
            "FROM ChatMessage cm WHERE cm.id = :messageId")
    Object[] getEditHistory(@Param("messageId") Long messageId);

}