package com.legalcase.repository;

import com.legalcase.entity.Comment;
import com.legalcase.entity.LegalCase;
import com.legalcase.entity.Task;
import com.legalcase.enums.CommentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {

    // Find all root comments (not replies) for a specific case
    @Query("SELECT c FROM Comment c LEFT JOIN FETCH c.author LEFT JOIN FETCH c.replies WHERE c.legalCase = :legalCase AND c.parentComment IS NULL ORDER BY c.createdAt ASC")
    List<Comment> findRootCommentsByLegalCase(@Param("legalCase") LegalCase legalCase);

    // Find all root comments (not replies) for a specific task
    @Query("SELECT c FROM Comment c LEFT JOIN FETCH c.author LEFT JOIN FETCH c.replies WHERE c.task = :task AND c.parentComment IS NULL ORDER BY c.createdAt ASC")
    List<Comment> findRootCommentsByTask(@Param("task") Task task);

    // Find all comments by a specific author
    List<Comment> findByAuthorId(Long authorId);

    // Find all comments mentioning a specific user
    @Query("SELECT c FROM Comment c WHERE c.mentions LIKE CONCAT('%', :userId, '%')")
    List<Comment> findCommentsMentioningUser(@Param("userId") String userId);

    // Count comments by type
    long countByType(CommentType type);

    // Find all comments for a case (including replies)

    @Query("SELECT c FROM Comment c LEFT JOIN FETCH c.author WHERE c.legalCase = :legalCase ORDER BY c.createdAt ASC")
    List<Comment> findAllByLegalCase(@Param("legalCase") LegalCase legalCase);

    // Find all comments for a task (including replies)
    @Query("SELECT c FROM Comment c LEFT JOIN FETCH c.author WHERE c.task = :task ORDER BY c.createdAt ASC")
    List<Comment> findAllByTask(@Param("task") Task task);
}