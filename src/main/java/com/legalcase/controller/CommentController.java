package com.legalcase.controller;

import com.legalcase.dto.request.CreateCommentRequest;
import com.legalcase.dto.request.UpdateCommentRequest;
import com.legalcase.dto.response.CommentResponse;
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

    @Operation(
            summary = "Create a comment",
            description = "Creates a new comment on a case, task, or as a reply to an existing comment. Supports @username mentions."
    )
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

        Long userId = extractUserId(httpRequest);
        boolean isAdmin = isAdmin(httpRequest);

        Comment comment = commentService.createComment(
                request.getContent(),
                request.getType(),
                request.getCaseId(),
                request.getTaskId(),
                userId,
                request.getParentCommentId(),
                request.getMentionedUsernames()
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CommentResponse.fromEntity(comment, userId, isAdmin));
    }

    @Operation(
            summary = "Get case comments (threaded)",
            description = "Returns all root comments for a case with their replies nested. Use for threaded view."
    )
    @GetMapping("/case/{caseId}")
    public ResponseEntity<List<CommentResponse>> getCommentsByCase(
            @Parameter(description = "Case ID") @PathVariable Long caseId,
            HttpServletRequest httpRequest) {

        Long userId = extractUserId(httpRequest);
        boolean isAdmin = isAdmin(httpRequest);

        List<Comment> comments = commentService.getRootCommentsByCase(caseId);
        List<CommentResponse> responses = comments.stream()
                .map(comment -> CommentResponse.fromEntity(comment, userId, isAdmin))
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    @Operation(
            summary = "Get case comments (flat list)",
            description = "Returns all comments for a case as a flat list (including replies). Good for search/export."
    )
    @GetMapping("/case/{caseId}/all")
    public ResponseEntity<List<CommentResponse>> getAllCommentsByCase(
            @Parameter(description = "Case ID") @PathVariable Long caseId,
            HttpServletRequest httpRequest) {

        Long userId = extractUserId(httpRequest);
        boolean isAdmin = isAdmin(httpRequest);

        List<Comment> comments = commentService.getAllCommentsByCase(caseId);
        List<CommentResponse> responses = comments.stream()
                .map(comment -> CommentResponse.fromEntity(comment, userId, isAdmin))
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    @Operation(
            summary = "Get task comments (threaded)",
            description = "Returns all root comments for a task with their replies nested."
    )
    @GetMapping("/task/{taskId}")
    public ResponseEntity<List<CommentResponse>> getCommentsByTask(
            @Parameter(description = "Task ID") @PathVariable Long taskId,
            HttpServletRequest httpRequest) {

        Long userId = extractUserId(httpRequest);
        boolean isAdmin = isAdmin(httpRequest);

        List<Comment> comments = commentService.getRootCommentsByTask(taskId);
        List<CommentResponse> responses = comments.stream()
                .map(comment -> CommentResponse.fromEntity(comment, userId, isAdmin))
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    @Operation(
            summary = "Get task comments (flat list)",
            description = "Returns all comments for a task as a flat list (including replies)."
    )
    @GetMapping("/task/{taskId}/all")
    public ResponseEntity<List<CommentResponse>> getAllCommentsByTask(
            @Parameter(description = "Task ID") @PathVariable Long taskId,
            HttpServletRequest httpRequest) {

        Long userId = extractUserId(httpRequest);
        boolean isAdmin = isAdmin(httpRequest);

        List<Comment> comments = commentService.getAllCommentsByTask(taskId);
        List<CommentResponse> responses = comments.stream()
                .map(comment -> CommentResponse.fromEntity(comment, userId, isAdmin))
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    @Operation(
            summary = "Get comment by ID",
            description = "Retrieves a single comment by its ID."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Comment found"),
            @ApiResponse(responseCode = "404", description = "Comment not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<CommentResponse> getCommentById(
            @Parameter(description = "Comment ID") @PathVariable Long id,
            HttpServletRequest httpRequest) {

        Long userId = extractUserId(httpRequest);
        boolean isAdmin = isAdmin(httpRequest);

        Comment comment = commentService.findById(id);
        return ResponseEntity.ok(CommentResponse.fromEntity(comment, userId, isAdmin));
    }

    @Operation(
            summary = "Update a comment",
            description = "Updates the content of a comment. Only the author can update."
    )
    @PutMapping("/{id}")
    public ResponseEntity<CommentResponse> updateComment(
            @Parameter(description = "Comment ID") @PathVariable Long id,
            @Valid @RequestBody UpdateCommentRequest request,
            HttpServletRequest httpRequest) {

        Long userId = extractUserId(httpRequest);
        boolean isAdmin = isAdmin(httpRequest);

        Comment comment = commentService.updateComment(id, request.getContent(), userId);
        return ResponseEntity.ok(CommentResponse.fromEntity(comment, userId, isAdmin));
    }

    @Operation(
            summary = "Delete a comment",
            description = "Deletes a comment. Only the author or admin can delete."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Comment deleted successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Not authorized to delete this comment")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteComment(
            @Parameter(description = "Comment ID") @PathVariable Long id,
            HttpServletRequest httpRequest) {

        Long userId = extractUserId(httpRequest);
        boolean isAdmin = isAdmin(httpRequest);

        commentService.deleteComment(id, userId, isAdmin);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Get comments mentioning me",
            description = "Returns all comments where the current user is @mentioned."
    )
    @GetMapping("/mentions/me")
    public ResponseEntity<List<CommentResponse>> getCommentsMentioningMe(HttpServletRequest httpRequest) {
        Long userId = extractUserId(httpRequest);
        boolean isAdmin = isAdmin(httpRequest);

        List<Comment> comments = commentService.getCommentsMentioningUser(userId);
        List<CommentResponse> responses = comments.stream()
                .map(comment -> CommentResponse.fromEntity(comment, userId, isAdmin))
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    private Long extractUserId(HttpServletRequest request) {
        String token = extractToken(request);
        return jwtUtils.getUserIdFromToken(token);
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
}