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

    @GetMapping("/cases/{caseId}/messages")
    public ResponseEntity<Page<ChatMessageResponse>> getMessagesByCase(
            @PathVariable Long caseId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            HttpServletRequest httpRequest) {

        Long userId = extractUserId(httpRequest);

        Page<ChatMessage> messages = chatService.getMessagesByCase(caseId, page, size, userId);
        Page<ChatMessageResponse> responses = messages.map(ChatMessageResponse::fromEntity);

        return ResponseEntity.ok(responses);
    }

    @GetMapping("/cases/{caseId}/messages/all")
    public ResponseEntity<List<ChatMessageResponse>> getAllMessagesByCase(
            @PathVariable Long caseId,
            HttpServletRequest httpRequest) {

        Long userId = extractUserId(httpRequest);

        List<ChatMessage> messages = chatService.getAllMessagesByCase(caseId, userId);
        List<ChatMessageResponse> responses = messages.stream()
                .map(ChatMessageResponse::fromEntity)
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    @GetMapping("/cases/{caseId}/unread")
    public ResponseEntity<List<ChatMessageResponse>> getUnreadMessagesByCase(
            @PathVariable Long caseId,
            HttpServletRequest httpRequest) {

        Long userId = extractUserId(httpRequest);

        List<ChatMessage> messages = chatService.getUnreadMessagesByCase(caseId, userId);
        List<ChatMessageResponse> responses = messages.stream()
                .map(ChatMessageResponse::fromEntity)
                .collect(Collectors.toList());

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
