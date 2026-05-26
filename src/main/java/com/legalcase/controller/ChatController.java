package com.legalcase.controller;

import com.legalcase.dto.request.DeleteMessageRequest;
import com.legalcase.dto.request.EditMessageRequest;
import com.legalcase.dto.request.MarkMessagesReadRequest;
import com.legalcase.dto.request.SendMessageRequest;
import com.legalcase.dto.response.*;
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

    // ============================================
    // SEND MESSAGE
    // ============================================

    @PostMapping("/messages")
    public ResponseEntity<ChatMessageResponse> sendMessage(
            @Valid @RequestBody SendMessageRequest request,
            HttpServletRequest httpRequest) {

        String userIdentifier = extractUserIdentifier(httpRequest);

        var message = chatService.sendMessage(
                request.getContent(),
                request.getType(),
                request.getCaseIdentifier(),
                userIdentifier,
                request.getFileUrl(),
                request.getFileName(),
                request.getFileSize(),
                request.getMentionedUserIdentifiers(),
                request.getMentionedTaskIdentifiers()
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ChatMessageResponse.fromEntity(message));
    }

    // ============================================
    // DELETE MESSAGE (NEW)
    // ============================================

    @Operation(
            summary = "Delete a message",
            description = "Soft deletes a message. Permissions: Admin (any), Lawyer (any in case), Sender (within 5 minutes)"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Message deleted successfully"),
            @ApiResponse(responseCode = "403", description = "Not authorized to delete this message"),
            @ApiResponse(responseCode = "404", description = "Message not found")
    })
    @DeleteMapping("/messages/{messageId}")
    public ResponseEntity<MessageDeletedResponse> deleteMessage(
            @Parameter(description = "Message ID") @PathVariable Long messageId,
            @RequestParam(required = false) String reason,
            HttpServletRequest httpRequest) {

        String userIdentifier = extractUserIdentifier(httpRequest);
        MessageDeletedResponse response = chatService.deleteMessage(messageId, userIdentifier, reason);
        return ResponseEntity.ok(response);
    }

    // ============================================
    // GET MESSAGES
    // ============================================

    @GetMapping("/cases/{caseIdentifier}/messages")
    public ResponseEntity<Page<ChatMessageResponse>> getMessagesByCase(
            @PathVariable String caseIdentifier,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            HttpServletRequest httpRequest) {

        String userIdentifier = extractUserIdentifier(httpRequest);
        Page<ChatMessageResponse> responses = chatService.getMessagesByCase(caseIdentifier, page, size, userIdentifier);
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/cases/{caseIdentifier}/messages/all")
    public ResponseEntity<List<ChatMessageResponse>> getAllMessagesByCase(
            @PathVariable String caseIdentifier,
            HttpServletRequest httpRequest) {

        String userIdentifier = extractUserIdentifier(httpRequest);
        List<ChatMessageResponse> responses = chatService.getAllMessagesByCase(caseIdentifier, userIdentifier);
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/cases/{caseIdentifier}/unread")
    public ResponseEntity<List<ChatMessageResponse>> getUnreadMessagesByCase(
            @PathVariable String caseIdentifier,
            HttpServletRequest httpRequest) {

        String userIdentifier = extractUserIdentifier(httpRequest);
        List<ChatMessageResponse> responses = chatService.getUnreadMessagesByCase(caseIdentifier, userIdentifier);
        return ResponseEntity.ok(responses);
    }

    // ============================================
    // UNREAD COUNTS & STATUS (ENHANCED)
    // ============================================

    @GetMapping("/unread/counts")
    public ResponseEntity<UnreadCountResponse> getUnreadCounts(HttpServletRequest httpRequest) {
        String userIdentifier = extractUserIdentifier(httpRequest);
        UnreadCountResponse response = chatService.getUnreadCounts(userIdentifier);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Get detailed unread status",
            description = "Returns detailed information about cases with unread messages including case details, unread counts, and last message previews"
    )
    @GetMapping("/unread/cases")
    public ResponseEntity<CaseUnreadStatusResponse> getDetailedUnreadStatus(HttpServletRequest httpRequest) {
        String userIdentifier = extractUserIdentifier(httpRequest);
        CaseUnreadStatusResponse response = chatService.getDetailedUnreadStatus(userIdentifier);
        return ResponseEntity.ok(response);
    }

    // ============================================
    // MARK MESSAGES AS READ
    // ============================================

    @PutMapping("/messages/read")
    public ResponseEntity<Void> markMessagesAsRead(
            @Valid @RequestBody MarkMessagesReadRequest request,
            HttpServletRequest httpRequest) {

        String userIdentifier = extractUserIdentifier(httpRequest);
        chatService.markMessagesAsRead(request.getMessageIds(), userIdentifier);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/cases/{caseIdentifier}/read")
    public ResponseEntity<Void> markAllMessagesAsRead(
            @PathVariable String caseIdentifier,
            HttpServletRequest httpRequest) {

        String userIdentifier = extractUserIdentifier(httpRequest);
        chatService.markAllMessagesAsReadInCase(caseIdentifier, userIdentifier);
        return ResponseEntity.ok().build();
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

    @Operation(
            summary = "Edit a message",
            description = "Partially update a message content. Permissions: Admin (any), Lawyer (any in case), Sender (within 10 minutes)"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Message edited successfully"),
            @ApiResponse(responseCode = "403", description = "Not authorized to edit this message"),
            @ApiResponse(responseCode = "404", description = "Message not found"),
            @ApiResponse(responseCode = "400", description = "Invalid request")
    })
    @PatchMapping("/messages/{messageId}")
    public ResponseEntity<MessageEditedResponse> editMessage(
            @PathVariable Long messageId,
            @Valid @RequestBody EditMessageRequest request,
            HttpServletRequest httpRequest) {

        String userIdentifier = extractUserIdentifier(httpRequest);
        MessageEditedResponse response = chatService.editMessage(
                messageId,
                request.getContent(),  // Changed from newContent
                userIdentifier,
                request.getReason()
        );
        return ResponseEntity.ok(response);
    }
}