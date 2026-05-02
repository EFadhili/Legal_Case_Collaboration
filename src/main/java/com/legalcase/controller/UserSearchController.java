package com.legalcase.controller;

import com.legalcase.dto.response.UserResponse;
import com.legalcase.entity.User;
import com.legalcase.security.JwtUtils;
import com.legalcase.service.UserService;
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
public class UserSearchController {

    private final UserService userService;
    private final JwtUtils jwtUtils;

    /**
     * Search for users by username, email, or full name (exact match).
     * Used for adding members to cases/tasks and mentions.
     * GET /api/users/search?q=johndoe
     */
    @GetMapping("/search")
    public ResponseEntity<List<UserResponse>> searchUsers(
            @RequestParam String q,
            HttpServletRequest request) {

        // Verify authentication (any authenticated user can search)
        extractUserId(request);

        List<User> users = userService.searchUsers(q);
        List<UserResponse> responses = users.stream()
                .map(UserResponse::fromEntity)
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    /**
     * Autocomplete endpoint for UI (partial matching).
     * GET /api/users/autocomplete?partial=john
     */
    @GetMapping("/autocomplete")
    public ResponseEntity<List<UserResponse>> autocompleteUsers(
            @RequestParam String partial,
            HttpServletRequest request) {

        extractUserId(request);

        List<User> users = userService.autocompleteUsers(partial);
        List<UserResponse> responses = users.stream()
                .map(UserResponse::fromEntity)
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    /**
     * Get user by username.
     * GET /api/users/username/johndoe
     */
    @GetMapping("/username/{username}")
    public ResponseEntity<UserResponse> getUserByUsername(@PathVariable String username) {
        User user = userService.findByUsername(username);
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


