package com.legalcase.controller;

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

    @Operation(summary = "Get all users (paginated)", description = "Admin only. Returns all users in the system.")
    @GetMapping
    public ResponseEntity<Page<UserResponse>> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {

        verifyAdmin(request);
        Page<User> users = userService.findAllUsers(PageRequest.of(page, size));
        return ResponseEntity.ok(users.map(UserResponse::fromEntity));
    }

    @Operation(summary = "Delete user by ID", description = "Admin only. Permanently deletes a user account.")
    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> deleteUserById(
            @Parameter(description = "User ID to delete") @PathVariable Long userId,
            HttpServletRequest request) {

        Long adminId = verifyAdmin(request);
        userService.deleteUserById(userId, adminId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Delete user by email", description = "Admin only. Permanently deletes a user account.")
    @DeleteMapping("/email/{email}")
    public ResponseEntity<Void> deleteUserByEmail(
            @Parameter(description = "Email of user to delete") @PathVariable String email,
            HttpServletRequest request) {

        Long adminId = verifyAdmin(request);
        userService.deleteUserByEmail(email, adminId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Deactivate user", description = "Admin only. Deactivates a user account.")
    @PatchMapping("/{userId}/deactivate")
    public ResponseEntity<Void> deactivateUser(
            @Parameter(description = "User ID to deactivate") @PathVariable Long userId,
            HttpServletRequest request) {

        Long adminId = verifyAdmin(request);
        userService.deactivateUser(userId, adminId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Activate user", description = "Admin only. Reactivates a user account.")
    @PatchMapping("/{userId}/activate")
    public ResponseEntity<Void> activateUser(
            @Parameter(description = "User ID to activate") @PathVariable Long userId,
            HttpServletRequest request) {

        verifyAdmin(request);
        userService.activateUser(userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Update user role", description = "Admin only. Changes a user's role.")
    @PatchMapping("/{userId}/role/{role}")
    public ResponseEntity<UserResponse> updateUserRole(
            @Parameter(description = "User ID") @PathVariable Long userId,
            @Parameter(description = "New role (ADMIN, LAWYER, STAFF)") @PathVariable String role,
            HttpServletRequest request) {

        verifyAdmin(request);
        User user = userService.updateUserRole(userId, role);
        return ResponseEntity.ok(UserResponse.fromEntity(user));
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

    private String extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new RuntimeException("Missing or invalid Authorization header");
        }
        return authHeader.substring(7);
    }
}