package com.legalcase.controller;

import com.legalcase.dto.request.MarkNotificationsReadRequest;
import com.legalcase.dto.response.NotificationResponse;
import com.legalcase.security.JwtUtils;
import com.legalcase.service.NotificationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
@Slf4j
public class NotificationController {

    private final NotificationService notificationService;
    private final JwtUtils jwtUtils;

    /**
     * Get notifications for current user (paginated).
     * GET /api/notifications?page=0&size=20
     */
    @GetMapping
    public ResponseEntity<Page<NotificationResponse>> getNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {

        Long userId = extractUserId(request);
        Page<NotificationResponse> notifications = notificationService.getNotificationsForUser(userId, page, size);
        return ResponseEntity.ok(notifications);
    }

    /**
     * Get unread notifications for current user.
     * GET /api/notifications/unread
     */
    @GetMapping("/unread")
    public ResponseEntity<List<NotificationResponse>> getUnreadNotifications(HttpServletRequest request) {
        Long userId = extractUserId(request);
        List<NotificationResponse> notifications = notificationService.getUnreadNotifications(userId);
        return ResponseEntity.ok(notifications);
    }

    /**
     * Get unread notification count.
     * GET /api/notifications/unread/count
     */
    @GetMapping("/unread/count")
    public ResponseEntity<Map<String, Long>> getUnreadCount(HttpServletRequest request) {
        Long userId = extractUserId(request);
        long count = notificationService.getUnreadCount(userId);

        Map<String, Long> response = new HashMap<>();
        response.put("count", count);
        return ResponseEntity.ok(response);
    }

    /**
     * Mark notifications as read.
     * PUT /api/notifications/read
     */
    @PutMapping("/read")
    public ResponseEntity<Void> markAsRead(
            @RequestBody MarkNotificationsReadRequest request,
            HttpServletRequest httpRequest) {

        Long userId = extractUserId(httpRequest);
        notificationService.markAsRead(request.getNotificationIds(), userId);
        return ResponseEntity.ok().build();
    }

    /**
     * Mark all notifications as read.
     * PUT /api/notifications/read/all
     */
    @PutMapping("/read/all")
    public ResponseEntity<Void> markAllAsRead(HttpServletRequest httpRequest) {
        Long userId = extractUserId(httpRequest);
        notificationService.markAllAsRead(userId);
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