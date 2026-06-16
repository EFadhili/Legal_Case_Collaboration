package com.legalcase.controller;

import com.legalcase.dto.request.ReactivateUserRequest;
import com.legalcase.dto.response.UserResponse;
import com.legalcase.entity.User;
import com.legalcase.security.JwtUtils;
import com.legalcase.service.UserService;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin User Management", description = "Administrative user management operations (Admin only)")
@SecurityRequirement(name = "Bearer Authentication")
public class UserController {

    private final UserService userService;
    private final JwtUtils jwtUtils;

    @Operation(summary = "Get all active users (paginated)", description = "Admin only. Returns all active (non-deleted) users.")
    @GetMapping
    public ResponseEntity<Page<UserResponse>> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {

        Long adminId = verifyAdmin(request);
        Page<User> users = userService.findAllActiveUsers(PageRequest.of(page, size));
        return ResponseEntity.ok(users.map(UserResponse::fromEntity));
    }

    @Operation(summary = "Soft delete user", description = "Admin only. Soft deletes a user account (30-day retention).")
    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> softDeleteUser(
            @Parameter(description = "User ID to delete") @PathVariable Long userId,
            @RequestParam(required = false) String reason,
            HttpServletRequest request) {

        Long adminId = verifyAdmin(request);
        String adminName = extractUserIdentifier(request);
        userService.softDeleteUser(userId, adminId, adminName, reason);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Reactivate user", description = "Admin only. Reactivates a soft-deleted user account.")
    @PatchMapping("/{userId}/reactivate")
    public ResponseEntity<UserResponse> reactivateUser(
            @Parameter(description = "User ID to reactivate") @PathVariable Long userId,
            @RequestBody(required = false) ReactivateUserRequest request,
            HttpServletRequest httpRequest) {

        Long adminId = verifyAdmin(httpRequest);
        String adminName = extractUserIdentifier(httpRequest);
        String reason = request != null ? request.getReason() : null;

        userService.reactivateUser(userId, adminId, adminName, reason);
        User user = userService.findById(userId);
        return ResponseEntity.ok(UserResponse.fromEntity(user));
    }

    @Operation(summary = "Deactivate user", description = "Admin only. Deactivates a user account (cannot login).")
    @PatchMapping("/{userId}/deactivate")
    public ResponseEntity<Void> deactivateUser(
            @Parameter(description = "User ID to deactivate") @PathVariable Long userId,
            HttpServletRequest request) {

        Long adminId = verifyAdmin(request);
        String adminName = extractUserIdentifier(request);
        userService.deactivateUser(userId, adminId, adminName);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Activate user", description = "Admin only. Reactivates a deactivated user account.")
    @PatchMapping("/{userId}/activate")
    public ResponseEntity<Void> activateUser(
            @Parameter(description = "User ID to activate") @PathVariable Long userId,
            HttpServletRequest request) {

        Long adminId = verifyAdmin(request);
        String adminName = extractUserIdentifier(request);
        userService.activateUser(userId, adminId, adminName);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Permanently delete user", description = "Admin only. Permanently deletes a user. Use with caution!")
    @DeleteMapping("/{userId}/permanent")
    public ResponseEntity<Void> permanentlyDeleteUser(
            @Parameter(description = "User ID to permanently delete") @PathVariable Long userId,
            HttpServletRequest request) {

        Long adminId = verifyAdmin(request);
        String adminName = extractUserIdentifier(request);
        userService.permanentlyDeleteUser(userId, adminId, adminName);
        return ResponseEntity.noContent().build();
    }

    private Long verifyAdmin(HttpServletRequest request) {
        Long userId = extractUserId(request);
        User admin = userService.findById(userId);
        if (!admin.isAdmin()) {
            throw new RuntimeException("Admin access required");
        }
        return userId;
    }

    private Long extractUserId(HttpServletRequest request) {
        String token = extractToken(request);
        return jwtUtils.getUserIdFromToken(token);
    }

    private String extractUserIdentifier(HttpServletRequest request) {
        String token = extractToken(request);
        return jwtUtils.getEmailFromToken(token);
    }

    private String extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new RuntimeException("Missing or invalid Authorization header");
        }
        return authHeader.substring(7);
    }

    @Operation(summary = "Promote user to ADMIN", description = "Admin only. Promotes a STAFF user to ADMIN.")
    @PatchMapping("/{userId}/promote-admin")
    public ResponseEntity<UserResponse> promoteToAdmin(
            @Parameter(description = "User ID") @PathVariable Long userId,
            HttpServletRequest request) {

        Long adminId = verifyAdmin(request);
        String adminName = extractUserIdentifier(request);
        User user = userService.promoteToAdmin(userId, adminId, adminName);
        return ResponseEntity.ok(UserResponse.fromEntity(user));
    }

    @Operation(summary = "Demote ADMIN to STAFF", description = "Admin only. Demotes an ADMIN user to STAFF.")
    @PatchMapping("/{userId}/demote-staff")
    public ResponseEntity<UserResponse> demoteToStaff(
            @Parameter(description = "User ID") @PathVariable Long userId,
            HttpServletRequest request) {

        Long adminId = verifyAdmin(request);
        String adminName = extractUserIdentifier(request);
        User user = userService.demoteToStaff(userId, adminId, adminName);
        return ResponseEntity.ok(UserResponse.fromEntity(user));
    }

}