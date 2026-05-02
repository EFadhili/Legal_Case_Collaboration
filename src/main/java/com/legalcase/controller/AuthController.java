package com.legalcase.controller;

import com.legalcase.dto.request.LoginRequest;
import com.legalcase.dto.request.RegisterRequest;
import com.legalcase.dto.response.AuthResponse;
import com.legalcase.dto.response.UserResponse;
import com.legalcase.entity.User;
import com.legalcase.security.JwtUtils;
import com.legalcase.service.UserService;
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
                    request.getFullName(),
                    request.getRole()
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
        log.info("Received login request for email: {}", request.getEmail());

        try {
            User authenticatedUser = userService.authenticate(
                    request.getEmail(),
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
            log.warn("Login failed for {}: {}", request.getEmail(), e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(AuthResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<UserResponse> getUserById(@PathVariable Long id) {
        log.info("Fetching user with ID: {}", id);

        try {
            User user = userService.findById(id);
            UserResponse response = UserResponse.fromEntity(user);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            log.error("User not found: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }


    // NEW: Check username availability endpoint
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

    // FIXED: Get current user from token, not from request attribute
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(HttpServletRequest request) {
        try {
            // Extract token from Authorization header
            String authHeader = request.getHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            String token = authHeader.substring(7);
            Long userId = jwtUtils.getUserIdFromToken(token);

            User user = userService.findById(userId);
            return ResponseEntity.ok(UserResponse.fromEntity(user));

        } catch (Exception e) {
            log.error("Failed to get current user: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }
}





