package com.legalcase.controller;

import com.legalcase.dto.request.LoginRequest;
import com.legalcase.dto.request.RegisterRequest;
import com.legalcase.dto.response.AuthResponse;
import com.legalcase.dto.response.UserResponse;
import com.legalcase.entity.User;
import com.legalcase.security.JwtUtils;
import com.legalcase.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
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

    @Operation(
            summary = "Register a new user",
            description = "Creates a new user account with the specified role. Returns JWT token upon success."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "User registered successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input data",
                    content = @Content(examples = @ExampleObject(value = """
                            {
                              "timestamp": "2026-05-18T10:30:00",
                              "status": 400,
                              "error": "Validation Failed",
                              "message": "Invalid request parameters",
                              "path": "/api/auth/register",
                              "errorCode": "VALIDATION_ERROR",
                              "details": {
                                "email": "Email is required",
                                "password": "Password must be at least 8 characters"
                              }
                            }"""))),
            @ApiResponse(responseCode = "409", description = "Email or username already exists")
    })

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

    @Operation(
            summary = "Login user",
            description = "Authenticates a user with email and password. Returns JWT token upon success."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Login successful"),
            @ApiResponse(responseCode = "401", description = "Invalid credentials"),
            @ApiResponse(responseCode = "400", description = "Validation error")
    })

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

    @Operation(
            summary = "Get user by ID",
            description = "Retrieves user information by their unique ID"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User found"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })

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

    @Operation(
            summary = "Check username availability",
            description = "Checks if a username is already taken"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Returns true if available, false if taken")
    })
    // NEW: Check username availability endpoint
    @GetMapping("/check-username")
    public ResponseEntity<Boolean> isUsernameAvailable(@RequestParam String username) {
        boolean isAvailable = userService.isUsernameAvailable(username);
        return ResponseEntity.ok(isAvailable);
    }

    @Operation(
            summary = "Check email availability",
            description = "Checks if an email address is already registered"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Returns true if available, false if taken")
    })
    @GetMapping("/check-email")
    public ResponseEntity<Boolean> isEmailAvailable(@RequestParam String email) {
        boolean isAvailable = userService.isEmailAvailable(email);
        return ResponseEntity.ok(isAvailable);
    }

    @Operation(
            summary = "Get current user",
            description = "Returns the authenticated user's profile information. Requires JWT token."
    )
    @SecurityRequirement(name = "Bearer Authentication")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User found"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid or missing token")
    })
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

    @Operation(
            summary = "Delete own account",
            description = "Allows a user to permanently delete their own account."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Account deleted successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteOwnAccount(HttpServletRequest request) {
        Long userId = extractUserId(request);
        userService.deleteUserById(userId, userId);
        return ResponseEntity.noContent().build();
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





