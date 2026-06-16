package com.legalcase.controller;

import com.legalcase.dto.request.LoginRequest;
import com.legalcase.dto.request.RegisterRequest;
import com.legalcase.dto.response.AuthResponse;
import com.legalcase.dto.response.UserResponse;
import com.legalcase.entity.User;
import com.legalcase.security.JwtUtils;
import com.legalcase.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authentication", description = "User authentication and registration endpoints")
public class AuthController {

    private final UserService userService;
    private final JwtUtils jwtUtils;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        log.info("Received registration request for email: {}", request.getEmail());

        try {
            User savedUser = userService.registerUser(
                    request.getUsername(),
                    request.getEmail(),
                    request.getPassword(),
                    request.getFullName()
                    // Role removed - always STAFF
            );

            String token = jwtUtils.generateToken(
                    savedUser.getId(),
                    savedUser.getEmail(),
                    savedUser.getRole().name()
            );

            UserResponse userResponse = UserResponse.fromEntity(savedUser);

            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(AuthResponse.successWithToken("User registered successfully", userResponse, token));

        } catch (RuntimeException e) {
            log.error("Registration failed: {}", e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(AuthResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("Received login request for identifier: {}", request.getIdentifier());

        try {
            User authenticatedUser = userService.authenticate(
                    request.getIdentifier(),
                    request.getPassword()
            );

            String token = jwtUtils.generateToken(
                    authenticatedUser.getId(),
                    authenticatedUser.getEmail(),
                    authenticatedUser.getRole().name()
            );

            UserResponse userResponse = UserResponse.fromEntity(authenticatedUser);

            return ResponseEntity
                    .ok()
                    .body(AuthResponse.successWithToken("Login successful", userResponse, token));

        } catch (RuntimeException e) {
            log.warn("Login failed for {}: {}", request.getIdentifier(), e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(AuthResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(HttpServletRequest request) {
        try {
            String token = extractToken(request);
            Long userId = jwtUtils.getUserIdFromToken(token);
            User user = userService.findById(userId);
            return ResponseEntity.ok(UserResponse.fromEntity(user));
        } catch (Exception e) {
            log.error("Failed to get current user: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteOwnAccount(
            @RequestParam(required = false) String reason,
            HttpServletRequest request) {
        Long userId = extractUserId(request);
        String userIdentifier = extractUserIdentifier(request);
        userService.softDeleteUser(userId, userId, userIdentifier, reason);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/check-username")
    public ResponseEntity<Boolean> isUsernameAvailable(@RequestParam String username) {
        boolean isAvailable = userService.isUsernameAvailable(username);
        return ResponseEntity.ok(isAvailable);
    }

    @GetMapping("/check-email")
    public ResponseEntity<Boolean> isEmailAvailable(@RequestParam String email) {
        boolean isAvailable = userService.isEmailAvailable(email);
        return ResponseEntity.ok(isAvailable);
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
}