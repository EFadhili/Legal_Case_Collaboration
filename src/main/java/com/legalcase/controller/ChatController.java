package com.legalcase.controller;

import com.legalcase.dto.request.MarkMessagesReadRequest;
import com.legalcase.dto.request.SendMessageRequest;
import com.legalcase.dto.response.ChatMessageResponse;
import com.legalcase.dto.response.UnreadCountResponse;
import com.legalcase.security.JwtUtils;
import com.legalcase.service.ChatService;
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
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Chat", description = "Real-time case-based messaging")
@SecurityRequirement(name = "Bearer Authentication")
public class ChatController {

    private final ChatService chatService;
    private final JwtUtils jwtUtils;

    @Operation(
            summary = "Send a message (REST)",
            description = "Sends a message to a case chat. Supports user mentions (@username) and task mentions (#taskId)."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Message sent successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "403", description = "Not a case member"),
            @ApiResponse(responseCode = "404", description = "Case not found")
    })
    @PostMapping("/messages")
    public ResponseEntity<ChatMessageResponse> sendMessage(
            @Valid @RequestBody SendMessageRequest request,
            HttpServletRequest httpRequest) {

        Long userId = extractUserId(httpRequest);

        var message = chatService.sendMessage(
                request.getContent(),
                request.getType(),
                request.getCaseId(),
                userId,
                request.getFileUrl(),
                request.getFileName(),
                request.getFileSize(),
                request.getMentionedUserIds(),
                request.getMentionedTaskIds()
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ChatMessageResponse.fromEntity(message));
    }

    @Operation(
            summary = "Get paginated messages",
            description = "Retrieves messages for a case with pagination. Latest messages first."
    )
    @GetMapping("/cases/{caseId}/messages")
    public ResponseEntity<Page<ChatMessageResponse>> getMessagesByCase(
            @Parameter(description = "Case ID") @PathVariable Long caseId,
            @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "50") int size,
            HttpServletRequest httpRequest) {

        Long userId = extractUserId(httpRequest);
        Page<ChatMessageResponse> responses = chatService.getMessagesByCase(caseId, page, size, userId);
        return ResponseEntity.ok(responses);
    }

    @Operation(
            summary = "Get all messages",
            description = "Retrieves all messages for a case (no pagination). Used for WebSocket initial load."
    )
    @GetMapping("/cases/{caseId}/messages/all")
    public ResponseEntity<List<ChatMessageResponse>> getAllMessagesByCase(
            @Parameter(description = "Case ID") @PathVariable Long caseId,
            HttpServletRequest httpRequest) {

        Long userId = extractUserId(httpRequest);
        List<ChatMessageResponse> responses = chatService.getAllMessagesByCase(caseId, userId);
        return ResponseEntity.ok(responses);
    }

    @Operation(
            summary = "Get unread messages",
            description = "Retrieves unread messages for a user in a specific case"
    )
    @GetMapping("/cases/{caseId}/unread")
    public ResponseEntity<List<ChatMessageResponse>> getUnreadMessagesByCase(
            @Parameter(description = "Case ID") @PathVariable Long caseId,
            HttpServletRequest httpRequest) {

        Long userId = extractUserId(httpRequest);
        List<ChatMessageResponse> responses = chatService.getUnreadMessagesByCase(caseId, userId);
        return ResponseEntity.ok(responses);
    }

    @Operation(
            summary = "Get unread counts",
            description = "Returns total unread messages count and breakdown by case for the current user"
    )
    @GetMapping("/unread/counts")
    public ResponseEntity<UnreadCountResponse> getUnreadCounts(HttpServletRequest httpRequest) {
        Long userId = extractUserId(httpRequest);
        UnreadCountResponse response = chatService.getUnreadCounts(userId);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Mark messages as read",
            description = "Marks specific messages as read for the current user"
    )
    @PutMapping("/messages/read")
    public ResponseEntity<Void> markMessagesAsRead(
            @Valid @RequestBody MarkMessagesReadRequest request,
            HttpServletRequest httpRequest) {

        Long userId = extractUserId(httpRequest);
        chatService.markMessagesAsRead(request.getMessageIds(), userId);
        return ResponseEntity.ok().build();
    }

    @Operation(
            summary = "Mark all messages as read",
            description = "Marks all messages in a case as read for the current user"
    )
    @PutMapping("/cases/{caseId}/read")
    public ResponseEntity<Void> markAllMessagesAsRead(
            @Parameter(description = "Case ID") @PathVariable Long caseId,
            HttpServletRequest httpRequest) {

        Long userId = extractUserId(httpRequest);
        chatService.markAllMessagesAsReadInCase(caseId, userId);
        return ResponseEntity.ok().build();
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
}