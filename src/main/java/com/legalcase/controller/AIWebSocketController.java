package com.legalcase.controller;

import com.legalcase.dto.request.AIQueryRequest;
import com.legalcase.dto.response.AIResponse;
import com.legalcase.security.JwtUtils;
import com.legalcase.service.AIService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Flux;

@Controller
@RequiredArgsConstructor
@Slf4j
public class AIWebSocketController {

    private final AIService aiService;
    private final JwtUtils jwtUtils;

    /**
     * Stream AI response to WebSocket client.
     * Client subscribes to: /topic/ai/stream/{sessionId}
     * Sends message to: /app/ai/stream/{sessionId}
     */
    @MessageMapping("/ai/stream/{sessionId}")
    @SendTo("/topic/ai/stream/{sessionId}")
    public Flux<String> streamResponse(
            @DestinationVariable String sessionId,
            @Payload AIQueryRequest request,
            SimpMessageHeaderAccessor headerAccessor) {

        log.info("WebSocket AI stream request for session: {}", sessionId);

        String userIdentifier = extractUserIdentifier(headerAccessor);

        // Process the query and stream the response
        return aiService.streamQuery(request, userIdentifier, sessionId);
    }

    /**
     * Alternative: Stream with conversation history
     */
    @MessageMapping("/ai/stream/conversation/{sessionId}")
    @SendTo("/topic/ai/stream/{sessionId}")
    public Flux<String> streamConversation(
            @DestinationVariable String sessionId,
            @Payload com.legalcase.dto.request.AIConversationRequest request,
            SimpMessageHeaderAccessor headerAccessor) {

        log.info("WebSocket AI conversation stream request for session: {}", sessionId);

        String userIdentifier = extractUserIdentifier(headerAccessor);

        // Build query request from conversation
        AIQueryRequest queryRequest = new AIQueryRequest();
        queryRequest.setPrompt(request.getMessage());
        queryRequest.setCaseId(request.getCaseId());

        return aiService.streamQuery(queryRequest, userIdentifier, sessionId);
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
}