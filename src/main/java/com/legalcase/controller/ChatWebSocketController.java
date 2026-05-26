package com.legalcase.controller;

import com.legalcase.dto.request.EditMessageRequest;
import com.legalcase.dto.request.SendMessageRequest;
import com.legalcase.dto.response.ChatMessageResponse;
import com.legalcase.dto.response.MessageDeletedResponse;
import com.legalcase.dto.response.MessageEditedResponse;
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

    @MessageMapping("/chat/{caseIdentifier}/send")
    @SendTo("/topic/chat/{caseIdentifier}")
    public ChatMessageResponse sendMessage(
            @DestinationVariable String caseIdentifier,
            @Payload SendMessageRequest request,
            SimpMessageHeaderAccessor headerAccessor) {

        log.info("WebSocket message received for case: {}", caseIdentifier);

        String userIdentifier = extractUserIdentifier(headerAccessor);
        String sessionCase = (String) headerAccessor.getSessionAttributes().get("caseIdentifier");
        chatService.validateWebSocketSession(sessionCase, caseIdentifier, userIdentifier);

        ChatMessage message = chatService.sendMessage(
                request.getContent(),
                request.getType(),
                caseIdentifier,
                userIdentifier,
                request.getFileUrl(),
                request.getFileName(),
                request.getFileSize(),
                request.getMentionedUserIdentifiers(),
                request.getMentionedTaskIdentifiers()
        );

        return ChatMessageResponse.fromEntity(message);
    }

    // NEW: WebSocket message deletion
    @MessageMapping("/chat/{caseIdentifier}/delete/{messageId}")
    @SendTo("/topic/chat/{caseIdentifier}/delete")
    public MessageDeletedResponse deleteMessage(
            @DestinationVariable String caseIdentifier,
            @DestinationVariable Long messageId,
            @Payload(required = false) String reason,
            SimpMessageHeaderAccessor headerAccessor) {

        log.info("WebSocket delete request for message: {} in case: {}", messageId, caseIdentifier);

        String userIdentifier = extractUserIdentifier(headerAccessor);
        String sessionCase = (String) headerAccessor.getSessionAttributes().get("caseIdentifier");
        chatService.validateWebSocketSession(sessionCase, caseIdentifier, userIdentifier);

        return chatService.deleteMessage(messageId, userIdentifier, reason);
    }

    @MessageMapping("/chat/{caseIdentifier}/join")
    @SendTo("/topic/chat/{caseIdentifier}/join")
    public String joinChat(
            @DestinationVariable String caseIdentifier,
            SimpMessageHeaderAccessor headerAccessor) {

        String userIdentifier = extractUserIdentifier(headerAccessor);

        if (!chatService.canAccessCaseChat(caseIdentifier, userIdentifier)) {
            log.warn("User {} denied access to case {} chat - not a member", userIdentifier, caseIdentifier);
            return "Access denied: You are not a member of this case";
        }

        log.info("User {} joined chat for case {}", userIdentifier, caseIdentifier);

        headerAccessor.getSessionAttributes().put("userIdentifier", userIdentifier);
        headerAccessor.getSessionAttributes().put("caseIdentifier", caseIdentifier);

        return userIdentifier + " joined the chat";
    }

    @MessageMapping("/chat/{caseIdentifier}/leave")
    @SendTo("/topic/chat/{caseIdentifier}/leave")
    public String leaveChat(
            @DestinationVariable String caseIdentifier,
            SimpMessageHeaderAccessor headerAccessor) {

        String userIdentifier = extractUserIdentifier(headerAccessor);

        headerAccessor.getSessionAttributes().remove("userIdentifier");
        headerAccessor.getSessionAttributes().remove("caseIdentifier");

        log.info("User {} left chat for case {}", userIdentifier, caseIdentifier);

        return userIdentifier + " left the chat";
    }

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

    @MessageMapping("/chat/{caseIdentifier}/edit/{messageId}")
    @SendTo("/topic/chat/{caseIdentifier}/edit")
    public MessageEditedResponse editMessage(
            @DestinationVariable String caseIdentifier,
            @DestinationVariable Long messageId,
            @Payload EditMessageRequest request,
            SimpMessageHeaderAccessor headerAccessor) {

        log.info("WebSocket edit request for message: {} in case: {}", messageId, caseIdentifier);

        String userIdentifier = extractUserIdentifier(headerAccessor);
        String sessionCase = (String) headerAccessor.getSessionAttributes().get("caseIdentifier");
        chatService.validateWebSocketSession(sessionCase, caseIdentifier, userIdentifier);

        return chatService.editMessage(messageId, request.getContent(), userIdentifier, request.getReason());
    }

}