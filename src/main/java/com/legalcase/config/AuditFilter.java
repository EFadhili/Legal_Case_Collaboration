package com.legalcase.config;

import com.legalcase.security.JwtUtils;
import com.legalcase.util.AuditContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class AuditFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        log.info("AuditFilter executing for request: {}", request.getRequestURI());

        try {
            // Capture IP address
            String ipAddress = getClientIpAddress(request);
            String userAgent = request.getHeader("User-Agent");

            AuditContext.setRequestInfo(ipAddress, userAgent);

            // Extract user info from token if present
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                if (jwtUtils.validateToken(token)) {
                    Long userId = jwtUtils.getUserIdFromToken(token);
                    String userIdentifier = jwtUtils.getEmailFromToken(token);
                    String userName = jwtUtils.getUserNameFromToken(token);
                    AuditContext.setCurrentUser(userId, userIdentifier, userName);
                }
            }

            filterChain.doFilter(request, response);

        } finally {
            // Clear thread-local after request completes
            AuditContext.clear();
        }
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }
}