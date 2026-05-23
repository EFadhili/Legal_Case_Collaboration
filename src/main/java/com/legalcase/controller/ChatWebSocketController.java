package com.legalcase.controller;

import com.legalcase.dto.request.SendMessageRequest;
import com.legalcase.dto.response.ChatMessageResponse;
import com.legalcase.entity.ChatMessage;
import com.legalcase.security.JwtUtils;
import com.legalcase.service.ChatService;
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
public class ChatWebSocketController {

    private final ChatService chatService;
    private final JwtUtils jwtUtils;

    @MessageMapping("/chat/{caseId}/send")
    @SendTo("/topic/chat/{caseId}")
    public ChatMessageResponse sendMessage(
            @DestinationVariable Long caseId,
            @Payload SendMessageRequest request,
            SimpMessageHeaderAccessor headerAccessor) {

        log.info("WebSocket message received for case: {}", caseId);

        // Extract user from JWT token
        Long userId = extractUserId(headerAccessor);

        // Send message (service will verify membership and create notifications)
        ChatMessage message = chatService.sendMessage(
                request.getContent(),
                request.getType(),
                caseId,
                userId,
                request.getFileUrl(),
                request.getFileName(),
                request.getFileSize(),
                request.getMentionedUserIds(),
                request.getMentionedTaskIds()
        );

        return ChatMessageResponse.fromEntity(message);
    }

    @MessageMapping("/chat/{caseId}/join")
    @SendTo("/topic/chat/{caseId}/join")
    public String joinChat(
            @DestinationVariable Long caseId,
            SimpMessageHeaderAccessor headerAccessor) {

        Long userId = extractUserId(headerAccessor);

        // Verify user is a case member before allowing join
        if (!chatService.canAccessCaseChat(caseId, userId)) {
            log.warn("User {} denied access to case {} chat - not a member", userId, caseId);
            return "Access denied: You are not a member of this case";
        }

        log.info("User {} joined chat for case {}", userId, caseId);

        // Store user info in session for future messages
        headerAccessor.getSessionAttributes().put("userId", userId);
        headerAccessor.getSessionAttributes().put("caseId", caseId);

        return "User joined the chat";
    }

    @MessageMapping("/chat/{caseId}/leave")
    @SendTo("/topic/chat/{caseId}/leave")
    public String leaveChat(
            @DestinationVariable Long caseId,
            SimpMessageHeaderAccessor headerAccessor) {

        Long userId = extractUserId(headerAccessor);

        log.info("User {} left chat for case {}", userId, caseId);

        return "User left the chat";
    }

    private Long extractUserId(SimpMessageHeaderAccessor headerAccessor) {
        // Try to get from session attributes (set during join)
        Object userIdObj = headerAccessor.getSessionAttributes().get("userId");
        if (userIdObj != null) {
            return (Long) userIdObj;
        }

        // Fallback: extract from Authorization header
        String authHeader = headerAccessor.getFirstNativeHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            return jwtUtils.getUserIdFromToken(token);
        }

        throw new RuntimeException("Cannot authenticate user for WebSocket connection");
    }
}

