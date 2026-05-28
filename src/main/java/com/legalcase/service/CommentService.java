package com.legalcase.service;

import com.legalcase.entity.Comment;
import com.legalcase.entity.LegalCase;
import com.legalcase.entity.Task;
import com.legalcase.entity.User;
import com.legalcase.enums.CommentType;
import com.legalcase.enums.Role;
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

import java.time.LocalDateTime;
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

    // ============================================
    // HELPER METHODS
    // ============================================

    private User findUserByIdentifier(String identifier) {
        return userRepository.findByUsername(identifier)
                .or(() -> userRepository.findByEmail(identifier))
                .orElseThrow(() -> new ResourceNotFoundException("User", "username or email", identifier));
    }

    private Long getUserIdFromIdentifier(String identifier) {
        return findUserByIdentifier(identifier).getId();
    }

    private LegalCase getCaseFromComment(Comment comment) {
        if (comment.getLegalCase() != null) {
            return comment.getLegalCase();
        } else if (comment.getTask() != null) {
            return comment.getTask().getLegalCase();
        }
        throw new BusinessException("Comment has no associated case or task");
    }

    private void verifyCaseMembership(LegalCase legalCase, User user) {
        if (!caseMemberRepository.existsByLegalCaseAndUser(legalCase, user)) {
            throw new AccessDeniedException("Only case members can comment in this case");
        }
    }

    private void verifyCaseMembership(Long caseId, Long userId) {
        LegalCase legalCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Case", caseId));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        verifyCaseMembership(legalCase, user);
    }

    private boolean isUserLawyerInCase(LegalCase legalCase, Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        return user != null && user.getRole() == Role.LAWYER;
    }

    // ============================================
    // CREATE COMMENT
    // ============================================

    @Transactional
    public Comment createComment(String content, CommentType type, Long caseId, Long taskId,
                                 String userIdentifier, Long parentCommentId, List<String> mentionedUsernames) {

        User author = findUserByIdentifier(userIdentifier);
        Long authorId = author.getId();

        Comment comment = new Comment();
        comment.setContent(content);
        comment.setAuthor(author);

        // Process mentions - support usernames
        if (mentionedUsernames != null && !mentionedUsernames.isEmpty()) {
            List<Long> mentionedUserIds = new ArrayList<>();
            for (String mentionedUsername : mentionedUsernames) {
                try {
                    User mentionedUser = findUserByIdentifier(mentionedUsername);
                    mentionedUserIds.add(mentionedUser.getId());
                    log.debug("User mentioned: {} -> ID {}", mentionedUsername, mentionedUser.getId());
                } catch (ResourceNotFoundException e) {
                    log.warn("Mentioned user not found: {}", mentionedUsername);
                }
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
            Comment parentComment = commentRepository.findCommentWithDetails(parentCommentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Parent Comment", parentCommentId));

            if (parentComment.isDeleted()) {
                log.warn("Replying to deleted comment: {}", parentCommentId);
            }

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
            verifyCaseMembership(legalCase, author);

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

            verifyCaseMembership(legalCase, author);
            comment.setLegalCase(legalCase);
            log.info("Creating new case comment for case ID: {}", caseId);
        }
        else if (type == CommentType.TASK) {
            if (taskId == null) {
                throw new ValidationException("taskId", "taskId is required for TASK comments");
            }
            Task task = taskRepository.findById(taskId)
                    .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));

            verifyCaseMembership(task.getLegalCase(), author);
            comment.setTask(task);
            log.info("Creating new task comment for task ID: {}, case ID: {}",
                    taskId, task.getLegalCase().getId());
        }

        Comment saved = commentRepository.save(comment);
        log.info("Comment created with ID: {}", saved.getId());

        return saved;
    }

    // ============================================
    // GET COMMENTS
    // ============================================

    public List<Comment> getRootCommentsByCase(Long caseId, String userIdentifier) {
        User user = findUserByIdentifier(userIdentifier);
        LegalCase legalCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Case", caseId));
        verifyCaseMembership(legalCase, user);

        List<Comment> rootComments = commentRepository.findRootCommentsByLegalCase(legalCase);

        return rootComments;
    }

    public List<Comment> getRootCommentsByTask(Long taskId, String userIdentifier) {
        User user = findUserByIdentifier(userIdentifier);
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));
        verifyCaseMembership(task.getLegalCase(), user);

        List<Comment> rootComments = commentRepository.findRootCommentsByTask(task);

        return rootComments;
    }

    public List<Comment> getAllCommentsByCase(Long caseId, String userIdentifier) {
        User user = findUserByIdentifier(userIdentifier);
        LegalCase legalCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Case", caseId));
        verifyCaseMembership(legalCase, user);
        return commentRepository.findAllByLegalCase(legalCase);
    }

    public List<Comment> getAllCommentsByTask(Long taskId, String userIdentifier) {
        User user = findUserByIdentifier(userIdentifier);
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));
        verifyCaseMembership(task.getLegalCase(), user);
        return commentRepository.findAllByTask(task);
    }

    public Comment findById(Long id, String userIdentifier) {
        User user = findUserByIdentifier(userIdentifier);
        Comment comment = commentRepository.findCommentWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Comment", id));
        verifyCaseMembership(getCaseFromComment(comment), user);
        return comment;
    }

    public List<Comment> getDeletedCommentsByCase(Long caseId, String userIdentifier) {
        User user = findUserByIdentifier(userIdentifier);
        LegalCase legalCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Case", caseId));
        verifyCaseMembership(legalCase, user);

        if (user.getRole() != Role.ADMIN && user.getRole() != Role.LAWYER) {
            throw new AccessDeniedException("Only admins and lawyers can view deleted comments");
        }

        return commentRepository.findDeletedCommentsByCase(legalCase);
    }

    // ============================================
    // UPDATE COMMENT (PATCH)
    // ============================================

    @Transactional
    public Comment updateComment(Long commentId, String newContent, String userIdentifier, String reason) {
        log.info("User {} updating comment: {}", userIdentifier, commentId);

        User user = findUserByIdentifier(userIdentifier);
        Comment comment = commentRepository.findCommentWithDetails(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment", commentId));

        LegalCase legalCase = getCaseFromComment(comment);
        verifyCaseMembership(legalCase, user);

        LocalDateTime now = LocalDateTime.now();
        boolean isAdmin = user.getRole() == Role.ADMIN;
        boolean isLawyer = user.getRole() == Role.LAWYER;
        boolean isAuthor = comment.getAuthor().getId().equals(user.getId());
        boolean isWithinTimeLimit = comment.getCreatedAt().isAfter(now.minusMinutes(10));

        if (isAdmin) {
            log.info("Admin {} editing comment {}", userIdentifier, commentId);
        }
        else if (isLawyer && isUserLawyerInCase(legalCase, user.getId())) {
            log.info("Lawyer {} editing comment {}", userIdentifier, commentId);
        }
        else if (isAuthor && isWithinTimeLimit) {
            log.info("User {} editing their own comment within time limit", userIdentifier);
        }
        else if (isAuthor && !isWithinTimeLimit) {
            throw new AccessDeniedException("You can only edit comments within 10 minutes of posting");
        }
        else {
            throw new AccessDeniedException("You don't have permission to edit this comment");
        }

        commentRepository.updateCommentContent(commentId, newContent, now, user.getId(), user.getFullName());

        comment = commentRepository.findCommentWithDetails(commentId).orElseThrow();
        log.info("Comment {} updated", commentId);

        return comment;
    }

    // ============================================
    // DELETE COMMENT (Soft Delete)
    // ============================================

    @Transactional
    public void deleteComment(Long commentId, String userIdentifier, String reason) {
        log.info("User {} deleting comment: {}", userIdentifier, commentId);

        User user = findUserByIdentifier(userIdentifier);
        Comment comment = commentRepository.findCommentWithDetails(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment", commentId));

        LegalCase legalCase = getCaseFromComment(comment);
        verifyCaseMembership(legalCase, user);

        LocalDateTime now = LocalDateTime.now();
        boolean isAdmin = user.getRole() == Role.ADMIN;
        boolean isLawyer = user.getRole() == Role.LAWYER;
        boolean isAuthor = comment.getAuthor().getId().equals(user.getId());
        boolean isWithinTimeLimit = comment.getCreatedAt().isAfter(now.minusMinutes(5));

        if (isAdmin) {
            log.info("Admin {} deleting comment {}", userIdentifier, commentId);
        }
        else if (isLawyer && isUserLawyerInCase(legalCase, user.getId())) {
            log.info("Lawyer {} deleting comment {}", userIdentifier, commentId);
        }
        else if (isAuthor && isWithinTimeLimit) {
            log.info("User {} deleting their own comment within time limit", userIdentifier);
        }
        else if (isAuthor && !isWithinTimeLimit) {
            throw new AccessDeniedException("You can only delete comments within 5 minutes of posting");
        }
        else {
            throw new AccessDeniedException("You don't have permission to delete this comment");
        }

        String deleteReason = (reason != null && !reason.isEmpty()) ? reason : "No reason provided";
        commentRepository.softDeleteComment(commentId, now, user.getId(), deleteReason);
        log.info("Comment {} soft deleted", commentId);
    }

    // ============================================
    // MENTIONS
    // ============================================

    public List<Comment> getCommentsMentioningUser(String userIdentifier) {
        User user = findUserByIdentifier(userIdentifier);
        String userIdStr = String.valueOf(user.getId());
        return commentRepository.findCommentsMentioningUserExact(userIdStr);
    }
}