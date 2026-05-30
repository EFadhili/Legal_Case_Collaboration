package com.legalcase.controller;

import com.legalcase.security.JwtUtils;
import com.legalcase.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
@Slf4j
public class NotificationWebSocketController {

    private final NotificationService notificationService;
    private final JwtUtils jwtUtils;

    /**
     * User subscribes to their personal notification topic.
     * Client should subscribe to: /topic/notifications/{userId}
     * This endpoint allows the server to verify the user before sending notifications.
     */
    @MessageMapping("/notifications/subscribe")
    public void subscribeToNotifications(
            @Payload String userIdentifier,
            SimpMessageHeaderAccessor headerAccessor) {

        String authenticatedUser = extractUserIdentifier(headerAccessor);

        // Verify the user is subscribing to their own notifications
        if (!authenticatedUser.equals(userIdentifier)) {
            log.warn("User {} attempted to subscribe to notifications for user {}", authenticatedUser, userIdentifier);
            throw new RuntimeException("You can only subscribe to your own notifications");
        }

        // Store user info in session
        headerAccessor.getSessionAttributes().put("userIdentifier", userIdentifier);
        log.info("User {} subscribed to notification stream", userIdentifier);
    }

    /**
     * Case members can subscribe to case-specific notifications.
     * Client should subscribe to: /topic/cases/{caseId}/notifications
     * Note: Actual subscription authorization should be handled by ChannelInterceptor.
     */
    @MessageMapping("/notifications/case/{caseId}/subscribe")
    public void subscribeToCaseNotifications(
            @Payload Long caseId,
            SimpMessageHeaderAccessor headerAccessor) {

        String userIdentifier = extractUserIdentifier(headerAccessor);

        // Store case subscription info
        headerAccessor.getSessionAttributes().put("caseId", caseId);
        log.info("User {} subscribed to notifications for case {}", userIdentifier, caseId);
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