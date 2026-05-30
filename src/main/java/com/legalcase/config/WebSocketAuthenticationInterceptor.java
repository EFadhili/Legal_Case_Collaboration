package com.legalcase.config;

import com.legalcase.security.JwtUtils;
import com.legalcase.service.WebSocketAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketAuthenticationInterceptor implements HandshakeInterceptor {

    private final JwtUtils jwtUtils;
    private final WebSocketAuthService webSocketAuthService;  // Changed from ChatService

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {

        if (request instanceof ServletServerHttpRequest servletRequest) {
            String token = extractTokenFromRequest(servletRequest);
            String caseIdentifier = extractCaseIdentifierFromRequest(servletRequest);

            if (token != null && jwtUtils.validateToken(token) && caseIdentifier != null) {
                String userIdentifier = jwtUtils.getEmailFromToken(token);

                // Verify user is a case member BEFORE establishing WebSocket connection
                if (webSocketAuthService.canAccessCaseChat(caseIdentifier, userIdentifier)) {
                    attributes.put("userIdentifier", userIdentifier);
                    attributes.put("caseIdentifier", caseIdentifier);
                    attributes.put("token", token);
                    log.info("WebSocket handshake authenticated for user: {} to case: {}", userIdentifier, caseIdentifier);
                    return true;
                } else {
                    log.warn("User {} is not a member of case {}, WebSocket connection denied", userIdentifier, caseIdentifier);
                    return false;
                }
            }
        }

        log.warn("WebSocket handshake authentication failed");
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // No-op
    }

    private String extractTokenFromRequest(ServletServerHttpRequest request) {
        // Try Authorization header first
        String authHeader = request.getServletRequest().getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        // Also try query parameter for WebSocket connections
        String token = request.getServletRequest().getParameter("token");
        if (token != null && !token.isEmpty()) {
            return token;
        }

        return null;
    }

    private String extractCaseIdentifierFromRequest(ServletServerHttpRequest request) {
        // Try query parameter first
        String caseIdentifier = request.getServletRequest().getParameter("caseIdentifier");
        if (caseIdentifier != null && !caseIdentifier.isEmpty()) {
            return caseIdentifier;
        }

        // Also try "caseId" parameter for backward compatibility
        String caseId = request.getServletRequest().getParameter("caseId");
        if (caseId != null && !caseId.isEmpty()) {
            return caseId;
        }

        return null;
    }
}