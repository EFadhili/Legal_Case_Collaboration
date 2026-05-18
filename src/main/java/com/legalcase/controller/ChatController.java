package com.legalcase.controller;

import com.legalcase.dto.request.MarkMessagesReadRequest;
import com.legalcase.dto.request.SendMessageRequest;
import com.legalcase.dto.response.ChatMessageResponse;
import com.legalcase.dto.response.UnreadCountResponse;
import com.legalcase.entity.ChatMessage;
import com.legalcase.security.JwtUtils;
import com.legalcase.service.ChatService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final ChatService chatService;
    private final JwtUtils jwtUtils;

    @PostMapping("/messages")
    public ResponseEntity<ChatMessageResponse> sendMessage(
            @Valid @RequestBody SendMessageRequest request,
            HttpServletRequest httpRequest) {

        Long userId = extractUserId(httpRequest);

        ChatMessage message = chatService.sendMessage(
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

    /**
     * Get messages for a case (paginated).
     * GET /api/chat/cases/{caseId}/messages?page=0&size=50
     */
    @GetMapping("/cases/{caseId}/messages")
    public ResponseEntity<Page<ChatMessageResponse>> getMessagesByCase(
            @PathVariable Long caseId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            HttpServletRequest httpRequest) {

        Long userId = extractUserId(httpRequest);

        // Now returns Page<ChatMessageResponse> directly from service
        Page<ChatMessageResponse> responses = chatService.getMessagesByCase(caseId, page, size, userId);

        return ResponseEntity.ok(responses);
    }


    /**
     * Get all messages for a case (for initial load).
     * GET /api/chat/cases/{caseId}/messages/all
     */
    @GetMapping("/cases/{caseId}/messages/all")
    public ResponseEntity<List<ChatMessageResponse>> getAllMessagesByCase(
            @PathVariable Long caseId,
            HttpServletRequest httpRequest) {

        Long userId = extractUserId(httpRequest);

        List<ChatMessageResponse> responses = chatService.getAllMessagesByCase(caseId, userId);

        return ResponseEntity.ok(responses);
    }

    /**
     * Get unread messages for a user in a specific case.
     * GET /api/chat/cases/{caseId}/unread
     */
    @GetMapping("/cases/{caseId}/unread")
    public ResponseEntity<List<ChatMessageResponse>> getUnreadMessagesByCase(
            @PathVariable Long caseId,
            HttpServletRequest httpRequest) {

        Long userId = extractUserId(httpRequest);

        List<ChatMessageResponse> responses = chatService.getUnreadMessagesByCase(caseId, userId);

        return ResponseEntity.ok(responses);
    }

    @GetMapping("/unread/counts")
    public ResponseEntity<UnreadCountResponse> getUnreadCounts(HttpServletRequest httpRequest) {
        Long userId = extractUserId(httpRequest);

        UnreadCountResponse response = chatService.getUnreadCounts(userId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/messages/read")
    public ResponseEntity<Void> markMessagesAsRead(
            @Valid @RequestBody MarkMessagesReadRequest request,
            HttpServletRequest httpRequest) {

        Long userId = extractUserId(httpRequest);

        chatService.markMessagesAsRead(request.getMessageIds(), userId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/cases/{caseId}/read")
    public ResponseEntity<Void> markAllMessagesAsRead(
            @PathVariable Long caseId,
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
