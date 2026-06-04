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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "User Search", description = "Search and retrieve user information")
@SecurityRequirement(name = "Bearer Authentication")
public class UserSearchController {

    private final UserService userService;
    private final JwtUtils jwtUtils;

    @Operation(summary = "Search users", description = "Searches for active users by username, email, or full name.")
    @GetMapping("/search")
    public ResponseEntity<List<UserResponse>> searchUsers(
            @Parameter(description = "Search term") @RequestParam String q,
            HttpServletRequest request) {

        extractUserId(request);
        List<User> users = userService.searchUsers(q);
        return ResponseEntity.ok(users.stream()
                .map(UserResponse::fromEntity)
                .collect(Collectors.toList()));
    }

    @Operation(summary = "Autocomplete users", description = "Partial matching for UI autocomplete (active users only).")
    @GetMapping("/autocomplete")
    public ResponseEntity<List<UserResponse>> autocompleteUsers(
            @Parameter(description = "Partial search term") @RequestParam String partial,
            HttpServletRequest request) {

        extractUserId(request);
        List<User> users = userService.autocompleteUsers(partial);
        return ResponseEntity.ok(users.stream()
                .map(UserResponse::fromEntity)
                .collect(Collectors.toList()));
    }

    @Operation(summary = "Get user by username", description = "Retrieves active user by username.")
    @GetMapping("/username/{username}")
    public ResponseEntity<UserResponse> getUserByUsername(
            @Parameter(description = "Username") @PathVariable String username) {
        User user = userService.findByUsername(username);
        return ResponseEntity.ok(UserResponse.fromEntity(user));
    }

    @Operation(summary = "Get user by email", description = "Retrieves active user by email.")
    @GetMapping("/email/{email}")
    public ResponseEntity<UserResponse> getUserByEmail(
            @Parameter(description = "Email") @PathVariable String email) {
        User user = userService.findByEmail(email);
        return ResponseEntity.ok(UserResponse.fromEntity(user));
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