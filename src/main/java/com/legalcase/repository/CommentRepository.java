package com.legalcase.repository;

import com.legalcase.entity.Comment;
import com.legalcase.entity.LegalCase;
import com.legalcase.entity.Task;
import com.legalcase.enums.CommentType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {

    // ============================================
    // OVERRIDE DEFAULT METHODS WITH @EntityGraph
    // ============================================

    @Override
    @EntityGraph(attributePaths = {
            "author",
            "legalCase",
            "task",
            "task.legalCase",
            "parentComment",
            "replies",
            "replies.author"
    })
    Optional<Comment> findById(Long id);

    // ============================================
    // FIND METHODS WITH @EntityGraph
    // ============================================

    @EntityGraph(attributePaths = {
            "author",
            "legalCase",
            "task",
            "task.legalCase",
            "parentComment",
            "replies",
            "replies.author"
    })
    Optional<Comment> findByIdAndIsDeletedFalse(Long id);

    @EntityGraph(attributePaths = {
            "author",
            "legalCase",
            "task",
            "task.legalCase",
            "parentComment",
            "replies",
            "replies.author"
    })
    @Query("SELECT c FROM Comment c WHERE c.id = :id")
    Optional<Comment> findCommentWithDetails(@Param("id") Long id);

    // ============================================
    // ROOT COMMENTS
    // ============================================

    @EntityGraph(attributePaths = {
            "author",
            "legalCase",
            "task",
            "task.legalCase",
            "parentComment",
            "replies",
            "replies.author"
    })
    @Query("""
        SELECT DISTINCT c
        FROM Comment c
        WHERE c.legalCase = :legalCase
        AND c.parentComment IS NULL
        AND c.isDeleted = false
        ORDER BY c.createdAt ASC
    """)
    List<Comment> findRootCommentsByLegalCase(@Param("legalCase") LegalCase legalCase);

    @EntityGraph(attributePaths = {
            "author",
            "legalCase",
            "task",
            "task.legalCase",
            "parentComment",
            "replies",
            "replies.author"
    })
    @Query("""
        SELECT DISTINCT c
        FROM Comment c
        WHERE c.task = :task
        AND c.parentComment IS NULL
        AND c.isDeleted = false
        ORDER BY c.createdAt ASC
    """)
    List<Comment> findRootCommentsByTask(@Param("task") Task task);

    // ============================================
    // REPLIES
    // ============================================

    @EntityGraph(attributePaths = {
            "author",
            "legalCase",
            "task",
            "task.legalCase",
            "parentComment"
    })
    @Query("""
        SELECT c
        FROM Comment c
        WHERE c.parentComment.id = :parentCommentId
        AND c.isDeleted = false
        ORDER BY c.createdAt ASC
    """)
    List<Comment> findRepliesByParentCommentId(@Param("parentCommentId") Long parentCommentId);

    // ============================================
    // OTHER FIND METHODS
    // ============================================

    @EntityGraph(attributePaths = {
            "author",
            "legalCase",
            "task",
            "task.legalCase"
    })
    List<Comment> findByAuthorIdAndIsDeletedFalse(Long authorId);

    @EntityGraph(attributePaths = {
            "author",
            "legalCase",
            "task",
            "task.legalCase",
            "parentComment",
            "replies",
            "replies.author"
    })
    @Query("""
        SELECT DISTINCT c
        FROM Comment c
        WHERE c.legalCase = :legalCase
        AND c.isDeleted = false
        ORDER BY c.createdAt ASC
    """)
    List<Comment> findAllByLegalCase(@Param("legalCase") LegalCase legalCase);

    @EntityGraph(attributePaths = {
            "author",
            "legalCase",
            "task",
            "task.legalCase",
            "parentComment",
            "replies",
            "replies.author"
    })
    @Query("""
        SELECT DISTINCT c
        FROM Comment c
        WHERE c.task = :task
        AND c.isDeleted = false
        ORDER BY c.createdAt ASC
    """)
    List<Comment> findAllByTask(@Param("task") Task task);

    long countByTypeAndIsDeletedFalse(CommentType type);

    // ============================================
    // SOFT DELETE
    // ============================================

    @Modifying
    @Query("""
        UPDATE Comment c
        SET c.isDeleted = true,
            c.deletedAt = :deletedAt,
            c.deletedBy = :deletedBy,
            c.deletedReason = :deletedReason
        WHERE c.id = :commentId
    """)
    void softDeleteComment(@Param("commentId") Long commentId,
                           @Param("deletedAt") LocalDateTime deletedAt,
                           @Param("deletedBy") Long deletedBy,
                           @Param("deletedReason") String deletedReason);

    @EntityGraph(attributePaths = {
            "author",
            "legalCase",
            "task",
            "task.legalCase"
    })
    @Query("""
        SELECT c
        FROM Comment c
        WHERE c.legalCase = :legalCase
        AND c.isDeleted = true
        ORDER BY c.deletedAt DESC
    """)
    List<Comment> findDeletedCommentsByCase(@Param("legalCase") LegalCase legalCase);

    // ============================================
    // UPDATE
    // ============================================

    @Modifying
    @Query("""
        UPDATE Comment c
        SET c.content = :newContent,
            c.isEdited = true,
            c.editedAt = :editedAt,
            c.editedBy = :editedBy,
            c.editedByName = :editedByName,
            c.originalContent =
                CASE
                    WHEN c.originalContent IS NULL
                    THEN c.content
                    ELSE c.originalContent
                END
        WHERE c.id = :commentId
        AND c.isDeleted = false
    """)
    void updateCommentContent(@Param("commentId") Long commentId,
                              @Param("newContent") String newContent,
                              @Param("editedAt") LocalDateTime editedAt,
                              @Param("editedBy") Long editedBy,
                              @Param("editedByName") String editedByName);

    // ============================================
    // REAL-TIME SUPPORT
    // ============================================

    @EntityGraph(attributePaths = {
            "author",
            "legalCase",
            "task",
            "task.legalCase"
    })
    @Query("""
        SELECT c
        FROM Comment c
        WHERE c.legalCase = :legalCase
        ORDER BY c.createdAt ASC
    """)
    List<Comment> findAllByLegalCaseIncludingDeleted(@Param("legalCase") LegalCase legalCase);

    @EntityGraph(attributePaths = {
            "author",
            "legalCase",
            "task",
            "task.legalCase"
    })
    @Query("""
        SELECT c
        FROM Comment c
        WHERE c.legalCase = :legalCase
        AND c.isDeleted = false
        AND c.createdAt > :since
        ORDER BY c.createdAt ASC
    """)
    List<Comment> findRecentCommentsByCase(@Param("legalCase") LegalCase legalCase,
                                           @Param("since") LocalDateTime since);

    // ============================================
    // MENTIONS
    // ============================================

    @EntityGraph(attributePaths = {
            "author",
            "legalCase",
            "task",
            "task.legalCase",
            "parentComment",
            "replies",
            "replies.author"
    })
    @Query("""
    SELECT DISTINCT c
    FROM Comment c
    WHERE c.mentions LIKE CONCAT('%', :userId, '%')
    AND c.isDeleted = false
""")
    List<Comment> findCommentsMentioningUser(@Param("userId") String userId);

    @EntityGraph(attributePaths = {
            "author",
            "legalCase",
            "task",
            "task.legalCase",
            "parentComment",
            "replies",
            "replies.author"
    })
    @Query("""
    SELECT DISTINCT c
    FROM Comment c
    WHERE c.mentions IS NOT NULL
    AND c.mentions != ''
    AND CONCAT(',', c.mentions, ',')
        LIKE CONCAT('%,', :userId, ',%')
    AND c.isDeleted = false
""")
    List<Comment> findCommentsMentioningUserExact(@Param("userId") String userId);
}