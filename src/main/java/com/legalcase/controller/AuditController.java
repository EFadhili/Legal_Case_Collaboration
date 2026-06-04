package com.legalcase.controller;

import com.legalcase.dto.response.AuditLogResponse;
import com.legalcase.dto.response.AuditStatisticsResponse;
import com.legalcase.entity.AuditLog;
import com.legalcase.enums.AuditAction;
import com.legalcase.enums.AuditStatus;
import com.legalcase.enums.EntityType;
import com.legalcase.security.JwtUtils;
import com.legalcase.service.AuditService;
import com.legalcase.service.UserService;
import org.springframework.data.domain.Pageable;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin/audit")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Audit Log (Admin)", description = "Admin-only audit log management")
@SecurityRequirement(name = "Bearer Authentication")
public class AuditController {

    private final AuditService auditService;
    private final UserService userService;
    private final JwtUtils jwtUtils;

    @Operation(summary = "Search audit logs", description = "Advanced search with multiple filters")
    @GetMapping("/search")
    public ResponseEntity<Page<AuditLogResponse>> searchAuditLogs(
            @Parameter(description = "User ID") @RequestParam(required = false) Long userId,
            @Parameter(description = "Action type") @RequestParam(required = false) AuditAction action,
            @Parameter(description = "Entity type") @RequestParam(required = false) EntityType entityType,
            @Parameter(description = "Entity ID") @RequestParam(required = false) Long entityId,
            @Parameter(description = "Status (SUCCESS/FAILURE)") @RequestParam(required = false) AuditStatus status,
            @Parameter(description = "Start date (ISO format)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @Parameter(description = "End date (ISO format)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @Parameter(description = "Page number") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "50") int size,
            HttpServletRequest request) {

        verifyAdmin(request);

        // Default date range if not provided (last 30 days)
        if (startDate == null) {
            startDate = LocalDateTime.now().minusDays(30);
        }
        if (endDate == null) {
            endDate = LocalDateTime.now();
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AuditLog> auditLogs = auditService.searchAuditLogs(
                userId, action, entityType, entityId, status, startDate, endDate, pageable
        );

        return ResponseEntity.ok(auditLogs.map(AuditLogResponse::fromEntity));
    }

    @Operation(summary = "Get audit logs by user")
    @GetMapping("/user/{userId}")
    public ResponseEntity<Page<AuditLogResponse>> getAuditLogsByUser(
            @Parameter(description = "User ID") @PathVariable Long userId,
            @Parameter(description = "Page number") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "50") int size,
            HttpServletRequest request) {

        verifyAdmin(request);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AuditLog> auditLogs = auditService.getAuditLogsByUser(userId, pageable);
        return ResponseEntity.ok(auditLogs.map(AuditLogResponse::fromEntity));
    }

    @Operation(summary = "Get audit logs by entity")
    @GetMapping("/entity/{entityType}/{entityId}")
    public ResponseEntity<Page<AuditLogResponse>> getAuditLogsByEntity(
            @Parameter(description = "Entity type") @PathVariable EntityType entityType,
            @Parameter(description = "Entity ID") @PathVariable Long entityId,
            @Parameter(description = "Page number") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "50") int size,
            HttpServletRequest request) {

        verifyAdmin(request);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AuditLog> auditLogs = auditService.getAuditLogsByEntity(entityType, entityId, pageable);
        return ResponseEntity.ok(auditLogs.map(AuditLogResponse::fromEntity));
    }

    @Operation(summary = "Get recent failures")
    @GetMapping("/failures")
    public ResponseEntity<List<AuditLogResponse>> getRecentFailures(
            @Parameter(description = "Number of failures to return") @RequestParam(defaultValue = "50") int limit,
            HttpServletRequest request) {

        verifyAdmin(request);
        List<AuditLog> failures = auditService.getRecentFailures(limit);
        return ResponseEntity.ok(failures.stream()
                .map(AuditLogResponse::fromEntity)
                .collect(Collectors.toList()));
    }

    @Operation(summary = "Get audit statistics")
    @GetMapping("/stats")
    public ResponseEntity<AuditStatisticsResponse> getAuditStatistics(HttpServletRequest request) {
        verifyAdmin(request);

        AuditStatisticsResponse stats = AuditStatisticsResponse.builder()
                .totalAudits(auditService.getTotalAuditCount())
                .build();

        return ResponseEntity.ok(stats);
    }

    private void verifyAdmin(HttpServletRequest request) {
        Long userId = extractUserId(request);
        if (!userService.findById(userId).isAdmin()) {
            throw new RuntimeException("Admin access required");
        }
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

    @PostMapping("/test-direct")
    public ResponseEntity<String> testDirectAudit(HttpServletRequest request) {
        try {
            // Get current user info from token
            String token = extractToken(request);
            Long userId = jwtUtils.getUserIdFromToken(token);
            String userEmail = jwtUtils.getEmailFromToken(token);

            // Direct audit without async
            auditService.recordAuditSync(
                    userId,
                    userEmail,
                    userEmail,
                    com.legalcase.enums.AuditAction.LOGIN_SUCCESS,
                    com.legalcase.enums.EntityType.USER,
                    userId,
                    "test-user",
                    null,
                    null,
                    "{\"test\": true}",
                    com.legalcase.enums.AuditStatus.SUCCESS,
                    null,
                    request.getRemoteAddr(),
                    request.getHeader("User-Agent")
            );

            return ResponseEntity.ok("Audit recorded successfully. User: " + userEmail);
        } catch (Exception e) {
            log.error("Test audit failed: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }
}