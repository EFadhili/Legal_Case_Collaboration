package com.legalcase.controller;

import com.legalcase.dto.request.CreateCommentRequest;
import com.legalcase.dto.request.UpdateCommentRequest;
import com.legalcase.dto.response.CommentEditedResponse;
import com.legalcase.dto.response.CommentResponse;
import com.legalcase.dto.response.DeleteCommentResponse;
import com.legalcase.entity.Comment;
import com.legalcase.security.JwtUtils;
import com.legalcase.service.CommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/comments")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Comments", description = "Case and task level comments with threading and mentions")
@SecurityRequirement(name = "Bearer Authentication")
public class CommentController {

    private final CommentService commentService;
    private final JwtUtils jwtUtils;

    // ============================================
    // CREATE COMMENT
    // ============================================

    @Operation(summary = "Create a comment", description = "Creates a new comment on a case, task, or as a reply to an existing comment. Supports @username mentions.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Comment created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Not a case member")
    })
    @PostMapping
    public ResponseEntity<CommentResponse> createComment(
            @Valid @RequestBody CreateCommentRequest request,
            HttpServletRequest httpRequest) {

        String userIdentifier = extractUserIdentifier(httpRequest);
        boolean isAdmin = isAdmin(httpRequest);
        boolean isLawyer = isLawyer(httpRequest);

        Comment comment = commentService.createComment(
                request.getContent(),
                request.getType(),
                request.getCaseId(),
                request.getTaskId(),
                userIdentifier,
                request.getParentCommentId(),
                request.getMentionedUsernames()
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CommentResponse.fromEntity(comment, userIdentifier, isAdmin, isLawyer,
                        comment.getLegalCase() != null ? comment.getLegalCase().getId() :
                                (comment.getTask() != null ? comment.getTask().getLegalCase().getId() : null)));
    }

    // ============================================
    // GET COMMENTS
    // ============================================

    @Operation(summary = "Get case comments (threaded)", description = "Returns all root comments for a case with their replies nested.")
    @GetMapping("/case/{caseId}")
    public ResponseEntity<List<CommentResponse>> getCommentsByCase(
            @Parameter(description = "Case ID") @PathVariable Long caseId,
            HttpServletRequest httpRequest) {

        String userIdentifier = extractUserIdentifier(httpRequest);
        boolean isAdmin = isAdmin(httpRequest);
        boolean isLawyer = isLawyer(httpRequest);

        List<Comment> comments = commentService.getRootCommentsByCase(caseId, userIdentifier);
        List<CommentResponse> responses = comments.stream()
                .map(comment -> CommentResponse.fromEntity(comment, userIdentifier, isAdmin, isLawyer, caseId))
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    @Operation(summary = "Get case comments (flat list)", description = "Returns all comments for a case as a flat list (including replies).")
    @GetMapping("/case/{caseId}/all")
    public ResponseEntity<List<CommentResponse>> getAllCommentsByCase(
            @Parameter(description = "Case ID") @PathVariable Long caseId,
            HttpServletRequest httpRequest) {

        String userIdentifier = extractUserIdentifier(httpRequest);
        boolean isAdmin = isAdmin(httpRequest);
        boolean isLawyer = isLawyer(httpRequest);

        List<Comment> comments = commentService.getAllCommentsByCase(caseId, userIdentifier);
        List<CommentResponse> responses = comments.stream()
                .map(comment -> CommentResponse.fromEntity(comment, userIdentifier, isAdmin, isLawyer, caseId))
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    @Operation(summary = "Get task comments (threaded)", description = "Returns all root comments for a task with their replies nested.")
    @GetMapping("/task/{taskId}")
    public ResponseEntity<List<CommentResponse>> getCommentsByTask(
            @Parameter(description = "Task ID") @PathVariable Long taskId,
            HttpServletRequest httpRequest) {

        String userIdentifier = extractUserIdentifier(httpRequest);
        boolean isAdmin = isAdmin(httpRequest);
        boolean isLawyer = isLawyer(httpRequest);

        List<Comment> comments = commentService.getRootCommentsByTask(taskId, userIdentifier);
        List<CommentResponse> responses = comments.stream()
                .map(comment -> CommentResponse.fromEntity(comment, userIdentifier, isAdmin, isLawyer, null))
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    @Operation(summary = "Get task comments (flat list)", description = "Returns all comments for a task as a flat list (including replies).")
    @GetMapping("/task/{taskId}/all")
    public ResponseEntity<List<CommentResponse>> getAllCommentsByTask(
            @Parameter(description = "Task ID") @PathVariable Long taskId,
            HttpServletRequest httpRequest) {

        String userIdentifier = extractUserIdentifier(httpRequest);
        boolean isAdmin = isAdmin(httpRequest);
        boolean isLawyer = isLawyer(httpRequest);

        List<Comment> comments = commentService.getAllCommentsByTask(taskId, userIdentifier);
        List<CommentResponse> responses = comments.stream()
                .map(comment -> CommentResponse.fromEntity(comment, userIdentifier, isAdmin, isLawyer, null))
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    @Operation(summary = "Get comment by ID", description = "Retrieves a single comment by its ID.")
    @GetMapping("/{id}")
    public ResponseEntity<CommentResponse> getCommentById(
            @Parameter(description = "Comment ID") @PathVariable Long id,
            HttpServletRequest httpRequest) {

        String userIdentifier = extractUserIdentifier(httpRequest);
        boolean isAdmin = isAdmin(httpRequest);
        boolean isLawyer = isLawyer(httpRequest);

        Comment comment = commentService.findById(id, userIdentifier);
        Long caseId = comment.getLegalCase() != null ? comment.getLegalCase().getId() :
                (comment.getTask() != null ? comment.getTask().getLegalCase().getId() : null);

        return ResponseEntity.ok(CommentResponse.fromEntity(comment, userIdentifier, isAdmin, isLawyer, caseId));
    }

    // ============================================
    // UPDATE COMMENT (PATCH)
    // ============================================

    @Operation(summary = "Update a comment", description = "Updates the content of a comment. Permissions: Admin (any), Lawyer (any in case), Author (within 10 minutes)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Comment updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Not authorized to edit this comment"),
            @ApiResponse(responseCode = "404", description = "Comment not found")
    })
    @PatchMapping("/{id}")
    public ResponseEntity<CommentResponse> updateComment(
            @Parameter(description = "Comment ID") @PathVariable Long id,
            @Valid @RequestBody UpdateCommentRequest request,
            HttpServletRequest httpRequest) {

        String userIdentifier = extractUserIdentifier(httpRequest);
        boolean isAdmin = isAdmin(httpRequest);
        boolean isLawyer = isLawyer(httpRequest);

        Comment comment = commentService.updateComment(id, request.getContent(), userIdentifier, request.getReason());

        Long caseId = comment.getLegalCase() != null ? comment.getLegalCase().getId() :
                (comment.getTask() != null ? comment.getTask().getLegalCase().getId() : null);

        return ResponseEntity.ok(CommentResponse.fromEntity(comment, userIdentifier, isAdmin, isLawyer, caseId));
    }

    // ============================================
    // DELETE COMMENT
    // ============================================

    @Operation(summary = "Delete a comment", description = "Soft deletes a comment. Permissions: Admin (any), Lawyer (any in case), Author (within 5 minutes)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Comment deleted successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Not authorized to delete this comment"),
            @ApiResponse(responseCode = "404", description = "Comment not found")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<DeleteCommentResponse> deleteComment(
            @Parameter(description = "Comment ID") @PathVariable Long id,
            @RequestParam(required = false) String reason,
            HttpServletRequest httpRequest) {

        String userIdentifier = extractUserIdentifier(httpRequest);

        Comment comment = commentService.findById(id, userIdentifier); // To get details for response
        commentService.deleteComment(id, userIdentifier, reason);

        DeleteCommentResponse response = DeleteCommentResponse.builder()
                .commentId(id)
                .caseId(comment.getLegalCase() != null ? comment.getLegalCase().getId() :
                        (comment.getTask() != null ? comment.getTask().getLegalCase().getId() : null))
                .caseNumber(comment.getLegalCase() != null ? comment.getLegalCase().getCaseNumber() :
                        (comment.getTask() != null ? comment.getTask().getLegalCase().getCaseNumber() : null))
                .taskId(comment.getTask() != null ? comment.getTask().getId() : null)
                .taskTitle(comment.getTask() != null ? comment.getTask().getTitle() : null)
                .deletedBy(Long.parseLong(extractUserIdentifier(httpRequest))) // Simplified
                .deletedByName(extractUserIdentifier(httpRequest)) // Would need full name
                .deletedAt(java.time.LocalDateTime.now())
                .reason(reason != null ? reason : "No reason provided")
                .isDeleted(true)
                .build();

        return ResponseEntity.ok(response);
    }

    // ============================================
    // DELETED COMMENTS (Admin/Lawyer only)
    // ============================================

    @Operation(summary = "Get deleted comments", description = "Returns all deleted comments for a case. Only accessible by admins and lawyers.")
    @GetMapping("/case/{caseId}/deleted")
    public ResponseEntity<List<CommentResponse>> getDeletedCommentsByCase(
            @Parameter(description = "Case ID") @PathVariable Long caseId,
            HttpServletRequest httpRequest) {

        String userIdentifier = extractUserIdentifier(httpRequest);
        boolean isAdmin = isAdmin(httpRequest);
        boolean isLawyer = isLawyer(httpRequest);

        List<Comment> comments = commentService.getDeletedCommentsByCase(caseId, userIdentifier);
        List<CommentResponse> responses = comments.stream()
                .map(comment -> CommentResponse.fromEntity(comment, userIdentifier, isAdmin, isLawyer, caseId))
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    // ============================================
    // MENTIONS
    // ============================================

    @Operation(summary = "Get comments mentioning me", description = "Returns all comments where the current user is @mentioned.")
    @GetMapping("/mentions/me")
    public ResponseEntity<List<CommentResponse>> getCommentsMentioningMe(HttpServletRequest httpRequest) {
        String userIdentifier = extractUserIdentifier(httpRequest);
        boolean isAdmin = isAdmin(httpRequest);
        boolean isLawyer = isLawyer(httpRequest);

        List<Comment> comments = commentService.getCommentsMentioningUser(userIdentifier);
        List<CommentResponse> responses = comments.stream()
                .map(comment -> {
                    Long caseId = comment.getLegalCase() != null ? comment.getLegalCase().getId() :
                            (comment.getTask() != null ? comment.getTask().getLegalCase().getId() : null);
                    return CommentResponse.fromEntity(comment, userIdentifier, isAdmin, isLawyer, caseId);
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    // ============================================
    // HELPER METHODS
    // ============================================

    private String extractUserIdentifier(HttpServletRequest request) {
        String token = extractToken(request);
        return jwtUtils.getEmailFromToken(token);
    }

    private String extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new RuntimeException("Missing or invalid Authorization header");
        }
        return authHeader.substring(7);
    }

    private boolean isAdmin(HttpServletRequest request) {
        try {
            String token = extractToken(request);
            String role = jwtUtils.getRoleFromToken(token);
            return "ADMIN".equals(role);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isLawyer(HttpServletRequest request) {
        try {
            String token = extractToken(request);
            String role = jwtUtils.getRoleFromToken(token);
            return "LAWYER".equals(role);
        } catch (Exception e) {
            return false;
        }
    }
}