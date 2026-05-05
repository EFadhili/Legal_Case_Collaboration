package com.legalcase.controller;

import com.legalcase.dto.request.CreateCommentRequest;
import com.legalcase.dto.request.UpdateCommentRequest;
import com.legalcase.dto.response.CommentResponse;
import com.legalcase.entity.Comment;
import com.legalcase.security.JwtUtils;
import com.legalcase.service.CommentService;
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
public class CommentController {

    private final CommentService commentService;
    private final JwtUtils jwtUtils;

    /**
     * Create a new comment (case comment, task comment, or reply).
     * POST /api/comments
     */
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

    /**
     * Get all root comments for a case (threaded structure).
     * GET /api/comments/case/{caseId}
     */
    @GetMapping("/case/{caseId}")
    public ResponseEntity<List<CommentResponse>> getCommentsByCase(
            @PathVariable Long caseId,
            HttpServletRequest httpRequest) {

        Long userId = extractUserId(httpRequest);
        boolean isAdmin = isAdmin(httpRequest);

        List<Comment> comments = commentService.getRootCommentsByCase(caseId);
        List<CommentResponse> responses = comments.stream()
                .map(comment -> CommentResponse.fromEntity(comment, userId, isAdmin))
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    /**
     * Get all comments for a case (flat list, including replies).
     * GET /api/comments/case/{caseId}/all
     */
    @GetMapping("/case/{caseId}/all")
    public ResponseEntity<List<CommentResponse>> getAllCommentsByCase(
            @PathVariable Long caseId,
            HttpServletRequest httpRequest) {

        Long userId = extractUserId(httpRequest);
        boolean isAdmin = isAdmin(httpRequest);

        List<Comment> comments = commentService.getAllCommentsByCase(caseId);
        List<CommentResponse> responses = comments.stream()
                .map(comment -> CommentResponse.fromEntity(comment, userId, isAdmin))
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    /**
     * Get all root comments for a task (threaded structure).
     * GET /api/comments/task/{taskId}
     */
    @GetMapping("/task/{taskId}")
    public ResponseEntity<List<CommentResponse>> getCommentsByTask(
            @PathVariable Long taskId,
            HttpServletRequest httpRequest) {

        Long userId = extractUserId(httpRequest);
        boolean isAdmin = isAdmin(httpRequest);

        List<Comment> comments = commentService.getRootCommentsByTask(taskId);
        List<CommentResponse> responses = comments.stream()
                .map(comment -> CommentResponse.fromEntity(comment, userId, isAdmin))
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    /**
     * Get all comments for a task (flat list, including replies).
     * GET /api/comments/task/{taskId}/all
     */
    @GetMapping("/task/{taskId}/all")
    public ResponseEntity<List<CommentResponse>> getAllCommentsByTask(
            @PathVariable Long taskId,
            HttpServletRequest httpRequest) {

        Long userId = extractUserId(httpRequest);
        boolean isAdmin = isAdmin(httpRequest);

        List<Comment> comments = commentService.getAllCommentsByTask(taskId);
        List<CommentResponse> responses = comments.stream()
                .map(comment -> CommentResponse.fromEntity(comment, userId, isAdmin))
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    /**
     * Get a single comment by ID.
     * GET /api/comments/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<CommentResponse> getCommentById(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {

        Long userId = extractUserId(httpRequest);
        boolean isAdmin = isAdmin(httpRequest);

        Comment comment = commentService.findById(id);
        return ResponseEntity.ok(CommentResponse.fromEntity(comment, userId, isAdmin));
    }

    /**
     * Update a comment (content only).
     * PUT /api/comments/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<CommentResponse> updateComment(
            @PathVariable Long id,
            @Valid @RequestBody UpdateCommentRequest request,
            HttpServletRequest httpRequest) {

        Long userId = extractUserId(httpRequest);
        boolean isAdmin = isAdmin(httpRequest);

        Comment comment = commentService.updateComment(id, request.getContent(), userId);
        return ResponseEntity.ok(CommentResponse.fromEntity(comment, userId, isAdmin));
    }

    /**
     * Delete a comment.
     * DELETE /api/comments/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteComment(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {

        Long userId = extractUserId(httpRequest);
        boolean isAdmin = isAdmin(httpRequest);

        commentService.deleteComment(id, userId, isAdmin);
        return ResponseEntity.noContent().build();
    }

    /**
     * Get all comments mentioning the current user.
     * GET /api/comments/mentions/me
     */
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