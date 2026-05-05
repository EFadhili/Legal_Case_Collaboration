package com.legalcase.service;

import com.legalcase.entity.Comment;
import com.legalcase.entity.LegalCase;
import com.legalcase.entity.Task;
import com.legalcase.entity.User;
import com.legalcase.enums.CommentType;
import com.legalcase.repository.CaseMemberRepository;
import com.legalcase.repository.CaseRepository;
import com.legalcase.repository.CommentRepository;
import com.legalcase.repository.TaskRepository;
import com.legalcase.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class CommentService {

    private final CommentRepository commentRepository;
    private final CaseRepository caseRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final CaseMemberRepository caseMemberRepository;

    /**
     * Create a new comment.
     *
     * For NEW comments (not replies):
     *   - Requires type (CASE or TASK) and corresponding caseId or taskId
     *
     * For REPLIES (parentCommentId provided):
     *   - Automatically inherits type, caseId/taskId from parent comment
     *   - type, caseId, taskId parameters are ignored
     *
     * @param content Comment text content
     * @param type Comment type (CASE/TASK) - ignored for replies
     * @param caseId Case ID - required for new CASE comments, ignored for replies
     * @param taskId Task ID - required for new TASK comments, ignored for replies
     * @param authorId ID of the user creating the comment
     * @param parentCommentId ID of parent comment (null if not a reply)
     * @param mentionedUsernames List of usernames mentioned in the comment
     * @return Created Comment entity
     */
    @Transactional
    public Comment createComment(String content, CommentType type, Long caseId, Long taskId,
                                 Long authorId, Long parentCommentId, List<String> mentionedUsernames) {

        User author = userRepository.findById(authorId)
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + authorId));

        Comment comment = new Comment();
        comment.setContent(content);
        comment.setAuthor(author);

        // Process mentions
        if (mentionedUsernames != null && !mentionedUsernames.isEmpty()) {
            List<Long> mentionedUserIds = new ArrayList<>();
            for (String username : mentionedUsernames) {
                userRepository.findByUsername(username)
                        .ifPresent(user -> mentionedUserIds.add(user.getId()));
            }
            if (!mentionedUserIds.isEmpty()) {
                String mentions = mentionedUserIds.stream()
                        .map(String::valueOf)
                        .collect(Collectors.joining(","));
                comment.setMentions(mentions);
                log.info("Comment mentions users: {}", mentionedUserIds);
            }
        }

        // ============================================
        // CASE 1: This is a REPLY to an existing comment
        // ============================================
        if (parentCommentId != null) {
            Comment parentComment = commentRepository.findById(parentCommentId)
                    .orElseThrow(() -> new RuntimeException("Parent comment not found with ID: " + parentCommentId));

            // Inherit type from parent comment (ignore the provided type if any)
            CommentType inheritedType = parentComment.getType();
            comment.setType(inheritedType);

            // Inherit case or task reference from parent
            LegalCase legalCase = null;
            Task task = null;

            if (inheritedType == CommentType.CASE) {
                legalCase = parentComment.getLegalCase();
                if (legalCase == null) {
                    throw new RuntimeException("Parent case comment has no case reference");
                }
                comment.setLegalCase(legalCase);
                log.info("Creating reply to case comment for case ID: {}", legalCase.getId());
            } else {
                task = parentComment.getTask();
                if (task == null) {
                    throw new RuntimeException("Parent task comment has no task reference");
                }
                comment.setTask(task);
                legalCase = task.getLegalCase();
                log.info("Creating reply to task comment for task ID: {}, case ID: {}",
                        task.getId(), legalCase.getId());
            }

            comment.setParentComment(parentComment);

            // Verify user is a case member
            if (!caseMemberRepository.existsByLegalCaseAndUser(legalCase, author)) {
                throw new RuntimeException("Only case members can reply to comments in this case");
            }

            Comment saved = commentRepository.save(comment);
            log.info("Reply created with ID: {} (inherited type: {})", saved.getId(), inheritedType);
            return saved;
        }

        // ============================================
        // CASE 2 & 3: NEW comments (must have type)
        // ============================================
        if (type == null) {
            throw new RuntimeException("Comment type is required for new comments (not replies)");
        }

        comment.setType(type);

        if (type == CommentType.CASE) {
            if (caseId == null) {
                throw new RuntimeException("caseId is required for CASE comments");
            }
            LegalCase legalCase = caseRepository.findById(caseId)
                    .orElseThrow(() -> new RuntimeException("Case not found with ID: " + caseId));

            if (!caseMemberRepository.existsByLegalCaseAndUser(legalCase, author)) {
                throw new RuntimeException("Only case members can comment on this case");
            }

            comment.setLegalCase(legalCase);
            log.info("Creating new case comment for case ID: {}", caseId);
        }
        else if (type == CommentType.TASK) {
            if (taskId == null) {
                throw new RuntimeException("taskId is required for TASK comments");
            }
            Task task = taskRepository.findById(taskId)
                    .orElseThrow(() -> new RuntimeException("Task not found with ID: " + taskId));

            if (!caseMemberRepository.existsByLegalCaseAndUser(task.getLegalCase(), author)) {
                throw new RuntimeException("Only case members can comment on tasks in this case");
            }

            comment.setTask(task);
            log.info("Creating new task comment for task ID: {}, case ID: {}",
                    taskId, task.getLegalCase().getId());
        }

        Comment saved = commentRepository.save(comment);
        log.info("Comment created with ID: {}", saved.getId());

        return saved;
    }

    /**
     * Get all root comments for a case (excluding replies, which are nested under their parents).
     */
    public List<Comment> getRootCommentsByCase(Long caseId) {
        LegalCase legalCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new RuntimeException("Case not found with ID: " + caseId));
        return commentRepository.findRootCommentsByLegalCase(legalCase);
    }

    /**
     * Get all root comments for a task (excluding replies, which are nested under their parents).
     */
    public List<Comment> getRootCommentsByTask(Long taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found with ID: " + taskId));
        return commentRepository.findRootCommentsByTask(task);
    }

    /**
     * Get all comments for a case (flat list, including replies).
     */
    public List<Comment> getAllCommentsByCase(Long caseId) {
        LegalCase legalCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new RuntimeException("Case not found with ID: " + caseId));
        return commentRepository.findAllByLegalCase(legalCase);
    }

    /**
     * Get all comments for a task (flat list, including replies).
     */
    public List<Comment> getAllCommentsByTask(Long taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found with ID: " + taskId));
        return commentRepository.findAllByTask(task);
    }

    /**
     * Get a single comment by ID.
     */
    public Comment findById(Long id) {
        return commentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Comment not found with ID: " + id));
    }

    /**
     * Update a comment (only content).
     */
    @Transactional
    public Comment updateComment(Long commentId, String newContent, Long userId) {
        log.info("User {} updating comment: {}", userId, commentId);

        Comment comment = findById(commentId);

        // Only author can edit
        if (!comment.getAuthor().getId().equals(userId)) {
            throw new RuntimeException("Only the author can edit this comment");
        }

        comment.setContent(newContent);
        return commentRepository.save(comment);
    }

    /**
     * Delete a comment.
     * If comment has replies, they will be cascaded (if CascadeType.ALL is configured).
     */
    @Transactional
    public void deleteComment(Long commentId, Long userId, boolean isAdmin) {
        log.info("User {} deleting comment: {}", userId, commentId);

        Comment comment = findById(commentId);

        // Only author or admin can delete
        if (!comment.getAuthor().getId().equals(userId) && !isAdmin) {
            throw new RuntimeException("Only the author or admin can delete this comment");
        }

        commentRepository.delete(comment);
        log.info("Comment {} deleted", commentId);
    }

    /**
     * Get all comments mentioning a specific user.
     */
    public List<Comment> getCommentsMentioningUser(Long userId) {
        return commentRepository.findCommentsMentioningUser(String.valueOf(userId));
    }
}