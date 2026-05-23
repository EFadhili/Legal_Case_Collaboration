package com.legalcase.controller;

import com.legalcase.dto.request.MarkNotificationsReadRequest;
import com.legalcase.dto.response.NotificationResponse;
import com.legalcase.security.JwtUtils;
import com.legalcase.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Notifications", description = "In-app notifications for user activities")
@SecurityRequirement(name = "Bearer Authentication")
public class NotificationController {

    private final NotificationService notificationService;
    private final JwtUtils jwtUtils;

    @Operation(
            summary = "Get notifications (paginated)",
            description = "Returns all notifications for the current user with pagination support."
    )
    @GetMapping
    public ResponseEntity<Page<NotificationResponse>> getNotifications(
            @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {

        Long userId = extractUserId(request);
        Page<NotificationResponse> notifications = notificationService.getNotificationsForUser(userId, page, size);
        return ResponseEntity.ok(notifications);
    }

    @Operation(
            summary = "Get unread notifications",
            description = "Returns all unread notifications for the current user."
    )
    @GetMapping("/unread")
    public ResponseEntity<List<NotificationResponse>> getUnreadNotifications(HttpServletRequest request) {
        Long userId = extractUserId(request);
        List<NotificationResponse> notifications = notificationService.getUnreadNotifications(userId);
        return ResponseEntity.ok(notifications);
    }

    @Operation(
            summary = "Get unread notification count",
            description = "Returns the total number of unread notifications for the current user."
    )
    @GetMapping("/unread/count")
    public ResponseEntity<Map<String, Long>> getUnreadCount(HttpServletRequest request) {
        Long userId = extractUserId(request);
        long count = notificationService.getUnreadCount(userId);

        Map<String, Long> response = new HashMap<>();
        response.put("count", count);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Mark notifications as read",
            description = "Marks specific notifications as read for the current user."
    )
    @PutMapping("/read")
    public ResponseEntity<Void> markAsRead(
            @RequestBody MarkNotificationsReadRequest request,
            HttpServletRequest httpRequest) {

        Long userId = extractUserId(httpRequest);
        notificationService.markAsRead(request.getNotificationIds(), userId);
        return ResponseEntity.ok().build();
    }

    @Operation(
            summary = "Mark all notifications as read",
            description = "Marks all notifications as read for the current user."
    )
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