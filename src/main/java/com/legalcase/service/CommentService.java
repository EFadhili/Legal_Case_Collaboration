package com.legalcase.service;

import com.legalcase.entity.Comment;
import com.legalcase.entity.LegalCase;
import com.legalcase.entity.Task;
import com.legalcase.entity.User;
import com.legalcase.enums.CommentType;
import com.legalcase.exception.*;
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

    @Transactional
    public Comment createComment(String content, CommentType type, Long caseId, Long taskId,
                                 Long authorId, Long parentCommentId, List<String> mentionedUsernames) {

        User author = userRepository.findById(authorId)
                .orElseThrow(() -> new ResourceNotFoundException("User", authorId));

        Comment comment = new Comment();
        comment.setContent(content);
        comment.setAuthor(author);

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
        // CASE 1: REPLY to an existing comment
        // ============================================
        if (parentCommentId != null) {
            Comment parentComment = commentRepository.findById(parentCommentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Parent Comment", parentCommentId));

            CommentType inheritedType = parentComment.getType();
            comment.setType(inheritedType);

            LegalCase legalCase = null;
            Task task = null;

            if (inheritedType == CommentType.CASE) {
                legalCase = parentComment.getLegalCase();
                if (legalCase == null) {
                    throw new BusinessException("Parent case comment has no case reference");
                }
                comment.setLegalCase(legalCase);
                log.info("Creating reply to case comment for case ID: {}", legalCase.getId());
            } else {
                task = parentComment.getTask();
                if (task == null) {
                    throw new BusinessException("Parent task comment has no task reference");
                }
                comment.setTask(task);
                legalCase = task.getLegalCase();
                log.info("Creating reply to task comment for task ID: {}, case ID: {}",
                        task.getId(), legalCase.getId());
            }

            comment.setParentComment(parentComment);

            if (!caseMemberRepository.existsByLegalCaseAndUser(legalCase, author)) {
                throw new AccessDeniedException("Only case members can reply to comments in this case");
            }

            Comment saved = commentRepository.save(comment);
            log.info("Reply created with ID: {} (inherited type: {})", saved.getId(), inheritedType);
            return saved;
        }

        // ============================================
        // CASE 2 & 3: NEW comments
        // ============================================
        if (type == null) {
            throw new ValidationException("type", "Comment type is required for new comments (not replies)");
        }

        comment.setType(type);

        if (type == CommentType.CASE) {
            if (caseId == null) {
                throw new ValidationException("caseId", "caseId is required for CASE comments");
            }
            LegalCase legalCase = caseRepository.findById(caseId)
                    .orElseThrow(() -> new ResourceNotFoundException("Case", caseId));

            if (!caseMemberRepository.existsByLegalCaseAndUser(legalCase, author)) {
                throw new AccessDeniedException("Only case members can comment on this case");
            }

            comment.setLegalCase(legalCase);
            log.info("Creating new case comment for case ID: {}", caseId);
        }
        else if (type == CommentType.TASK) {
            if (taskId == null) {
                throw new ValidationException("taskId", "taskId is required for TASK comments");
            }
            Task task = taskRepository.findById(taskId)
                    .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));

            if (!caseMemberRepository.existsByLegalCaseAndUser(task.getLegalCase(), author)) {
                throw new AccessDeniedException("Only case members can comment on tasks in this case");
            }

            comment.setTask(task);
            log.info("Creating new task comment for task ID: {}, case ID: {}",
                    taskId, task.getLegalCase().getId());
        }

        Comment saved = commentRepository.save(comment);
        log.info("Comment created with ID: {}", saved.getId());

        return saved;
    }

    public List<Comment> getRootCommentsByCase(Long caseId) {
        LegalCase legalCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Case", caseId));
        return commentRepository.findRootCommentsByLegalCase(legalCase);
    }

    public List<Comment> getRootCommentsByTask(Long taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));
        return commentRepository.findRootCommentsByTask(task);
    }

    public List<Comment> getAllCommentsByCase(Long caseId) {
        LegalCase legalCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Case", caseId));
        return commentRepository.findAllByLegalCase(legalCase);
    }

    public List<Comment> getAllCommentsByTask(Long taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));
        return commentRepository.findAllByTask(task);
    }

    public Comment findById(Long id) {
        return commentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Comment", id));
    }

    @Transactional
    public Comment updateComment(Long commentId, String newContent, Long userId) {
        log.info("User {} updating comment: {}", userId, commentId);

        Comment comment = findById(commentId);

        if (!comment.getAuthor().getId().equals(userId)) {
            throw new UnauthorizedException("Only the author can edit this comment");
        }

        comment.setContent(newContent);
        return commentRepository.save(comment);
    }

    @Transactional
    public void deleteComment(Long commentId, Long userId, boolean isAdmin) {
        log.info("User {} deleting comment: {}", userId, commentId);

        Comment comment = findById(commentId);

        if (!comment.getAuthor().getId().equals(userId) && !isAdmin) {
            throw new UnauthorizedException("Only the author or admin can delete this comment");
        }

        commentRepository.delete(comment);
        log.info("Comment {} deleted", commentId);
    }

    public List<Comment> getCommentsMentioningUser(Long userId) {
        return commentRepository.findCommentsMentioningUser(String.valueOf(userId));
    }
}