package com.legalcase.controller;

import com.legalcase.dto.request.CreateCommentRequest;
import com.legalcase.dto.request.UpdateCommentRequest;
import com.legalcase.dto.response.CommentEditedResponse;
import com.legalcase.dto.response.CommentResponse;
import com.legalcase.dto.response.DeleteCommentResponse;
import com.legalcase.entity.Comment;
import com.legalcase.security.JwtUtils;
import com.legalcase.service.CommentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
@Slf4j
public class CommentWebSocketController {

    private final CommentService commentService;
    private final JwtUtils jwtUtils;

    // ============================================
    // NEW COMMENT - Case level
    // ============================================

    @MessageMapping("/comments/case/{caseId}/new")
    @SendTo("/topic/comments/case/{caseId}")
    public CommentResponse newCaseComment(
            @DestinationVariable Long caseId,
            @Payload CreateCommentRequest request,
            SimpMessageHeaderAccessor headerAccessor) {

        log.info("WebSocket new case comment request for case: {}", caseId);

        String userIdentifier = extractUserIdentifier(headerAccessor);
        boolean isAdmin = isAdmin(headerAccessor);
        boolean isLawyer = isLawyer(headerAccessor);

        request.setType(com.legalcase.enums.CommentType.CASE);
        request.setCaseId(caseId);

        Comment comment = commentService.createComment(
                request.getContent(),
                request.getType(),
                caseId,
                null,
                userIdentifier,
                request.getParentCommentId(),
                request.getMentionedUsernames()
        );

        return CommentResponse.fromEntity(comment, userIdentifier, isAdmin, isLawyer, caseId);
    }

    // ============================================
    // NEW COMMENT - Task level
    // ============================================

    @MessageMapping("/comments/task/{taskId}/new")
    @SendTo("/topic/comments/task/{taskId}")
    public CommentResponse newTaskComment(
            @DestinationVariable Long taskId,
            @Payload CreateCommentRequest request,
            SimpMessageHeaderAccessor headerAccessor) {

        log.info("WebSocket new task comment request for task: {}", taskId);

        String userIdentifier = extractUserIdentifier(headerAccessor);
        boolean isAdmin = isAdmin(headerAccessor);
        boolean isLawyer = isLawyer(headerAccessor);

        request.setType(com.legalcase.enums.CommentType.TASK);
        request.setTaskId(taskId);

        Comment comment = commentService.createComment(
                request.getContent(),
                request.getType(),
                null,
                taskId,
                userIdentifier,
                request.getParentCommentId(),
                request.getMentionedUsernames()
        );

        Long caseId = comment.getTask() != null ? comment.getTask().getLegalCase().getId() : null;
        return CommentResponse.fromEntity(comment, userIdentifier, isAdmin, isLawyer, caseId);
    }

    // ============================================
    // EDIT COMMENT
    // ============================================

    @MessageMapping("/comments/{commentId}/edit")
    @SendTo("/topic/comments/{commentId}/edit")
    public CommentEditedResponse editComment(
            @DestinationVariable Long commentId,
            @Payload UpdateCommentRequest request,
            SimpMessageHeaderAccessor headerAccessor) {

        log.info("WebSocket edit request for comment: {}", commentId);

        String userIdentifier = extractUserIdentifier(headerAccessor);

        // Get original comment before edit (for response)
        Comment originalComment = commentService.findById(commentId, userIdentifier);

        Comment updatedComment = commentService.updateComment(
                commentId,
                request.getContent(),
                userIdentifier,
                request.getReason()
        );

        return CommentEditedResponse.builder()
                .commentId(commentId)
                .caseId(originalComment.getLegalCase() != null ? originalComment.getLegalCase().getId() :
                        (originalComment.getTask() != null ? originalComment.getTask().getLegalCase().getId() : null))
                .caseNumber(originalComment.getLegalCase() != null ? originalComment.getLegalCase().getCaseNumber() :
                        (originalComment.getTask() != null ? originalComment.getTask().getLegalCase().getCaseNumber() : null))
                .taskId(originalComment.getTask() != null ? originalComment.getTask().getId() : null)
                .taskTitle(originalComment.getTask() != null ? originalComment.getTask().getTitle() : null)
                .oldContent(originalComment.getContent())
                .newContent(updatedComment.getContent())
                .editedBy(originalComment.getAuthor().getId())
                .editedByName(originalComment.getAuthor().getFullName())
                .editedAt(java.time.LocalDateTime.now())
                .reason(request.getReason())
                .isEdited(true)
                .build();
    }

    // ============================================
    // DELETE COMMENT
    // ============================================

    @MessageMapping("/comments/{commentId}/delete")
    @SendTo("/topic/comments/{commentId}/delete")
    public DeleteCommentResponse deleteComment(
            @DestinationVariable Long commentId,
            @Payload(required = false) String reason,
            SimpMessageHeaderAccessor headerAccessor) {

        log.info("WebSocket delete request for comment: {}", commentId);

        String userIdentifier = extractUserIdentifier(headerAccessor);

        // Get comment before deletion (for response)
        Comment comment = commentService.findById(commentId, userIdentifier);

        commentService.deleteComment(commentId, userIdentifier, reason);

        return DeleteCommentResponse.builder()
                .commentId(commentId)
                .caseId(comment.getLegalCase() != null ? comment.getLegalCase().getId() :
                        (comment.getTask() != null ? comment.getTask().getLegalCase().getId() : null))
                .caseNumber(comment.getLegalCase() != null ? comment.getLegalCase().getCaseNumber() :
                        (comment.getTask() != null ? comment.getTask().getLegalCase().getCaseNumber() : null))
                .taskId(comment.getTask() != null ? comment.getTask().getId() : null)
                .taskTitle(comment.getTask() != null ? comment.getTask().getTitle() : null)
                .deletedBy(comment.getAuthor().getId())
                .deletedByName(comment.getAuthor().getFullName())
                .deletedAt(java.time.LocalDateTime.now())
                .reason(reason != null ? reason : "No reason provided")
                .isDeleted(true)
                .build();
    }

    // ============================================
    // HELPER METHODS
    // ============================================

    private String extractUserIdentifier(SimpMessageHeaderAccessor headerAccessor) {
        Object userIdentifierObj = headerAccessor.getSessionAttributes().get("userIdentifier");
        if (userIdentifierObj != null) {
            return (String) userIdentifierObj;
        }

        String authHeader = headerAccessor.getFirstNativeHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            return jwtUtils.getEmailFromToken(token);
        }

        throw new RuntimeException("Cannot authenticate user for WebSocket connection");
    }

    private boolean isAdmin(SimpMessageHeaderAccessor headerAccessor) {
        try {
            String authHeader = headerAccessor.getFirstNativeHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                String role = jwtUtils.getRoleFromToken(token);
                return "ADMIN".equals(role);
            }
        } catch (Exception e) {
            // Ignore
        }
        return false;
    }

    private boolean isLawyer(SimpMessageHeaderAccessor headerAccessor) {
        try {
            String authHeader = headerAccessor.getFirstNativeHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                String role = jwtUtils.getRoleFromToken(token);
                return "LAWYER".equals(role);
            }
        } catch (Exception e) {
            // Ignore
        }
        return false;
    }
}

