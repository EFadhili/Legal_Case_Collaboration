package com.legalcase.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legalcase.dto.request.LoginRequest;
import com.legalcase.dto.request.RegisterRequest;
import com.legalcase.dto.response.AuthResponse;
import com.legalcase.dto.response.UserResponse;
import com.legalcase.entity.User;
import com.legalcase.enums.Role;
import com.legalcase.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * UNIT TESTS for AuthController.
 * These tests mock the UserService to test ONLY the controller logic.
 * No database involved - very fast!
 */
@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(AuthController.class)
@DisplayName("Auth Controller Unit Tests")
class AuthControllerUnitTest {

    @Autowired
    private MockMvc mockMvc;  // Simulates HTTP requests

    @Autowired
    private ObjectMapper objectMapper;  // Converts Java objects to JSON

    @MockBean
    private UserService userService;  // Mocked - we control its behavior

    private RegisterRequest validRegisterRequest;
    private LoginRequest validLoginRequest;
    private User mockUser;
    private UserResponse mockUserResponse;

    @BeforeEach
    void setUp() {
        // Create a valid registration request (used in multiple tests)
        validRegisterRequest = new RegisterRequest();
        validRegisterRequest.setEmail("test@example.com");
        validRegisterRequest.setPassword("SecurePass123!");
        validRegisterRequest.setFullName("Test User");
        validRegisterRequest.setRole(Role.STAFF);

        // Create a valid login request
        validLoginRequest = new LoginRequest();
        validLoginRequest.setEmail("test@example.com");
        validLoginRequest.setPassword("SecurePass123!");

        // Create a mock User entity (what the service would return)
        mockUser = new User();
        mockUser.setId(1L);
        mockUser.setEmail("test@example.com");
        mockUser.setFullName("Test User");
        mockUser.setRole(Role.STAFF);
        mockUser.setActive(true);
        mockUser.setCreatedAt(LocalDateTime.now());
        mockUser.setUpdatedAt(LocalDateTime.now());

        // Create mock UserResponse DTO
        mockUserResponse = UserResponse.fromEntity(mockUser);
    }

    // ============================================
    // REGISTRATION TESTS
    // ============================================

    @Test
    @DisplayName("POST /auth/register - Should return 201 when registration succeeds")
    void register_Success_Returns201() throws Exception {
        // GIVEN: UserService will successfully register
        when(userService.registerUser(
                eq("test@example.com"),
                eq("SecurePass123!"),
                eq("Test User"),
                eq(Role.STAFF)
        )).thenReturn(mockUser);

        // WHEN & THEN: Perform POST request
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRegisterRequest)))
                .andExpect(status().isCreated())  // 201 Created
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("User registered successfully"))
                .andExpect(jsonPath("$.user.email").value("test@example.com"))
                .andExpect(jsonPath("$.user.fullName").value("Test User"))
                .andExpect(jsonPath("$.user.password").doesNotExist()); // Password NOT in response

        // Verify service was called exactly once
        verify(userService, times(1)).registerUser(
                eq("test@example.com"),
                eq("SecurePass123!"),
                eq("Test User"),
                eq(Role.STAFF)
        );
    }

    @Test
    @DisplayName("POST /auth/register - Should return 409 when email already exists")
    void register_EmailExists_Returns409() throws Exception {
        // GIVEN: UserService throws exception for duplicate email
        when(userService.registerUser(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("User with email test@example.com already exists"));

        // WHEN & THEN: Perform POST request
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRegisterRequest)))
                .andExpect(status().isConflict())  // 409 Conflict
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("User with email test@example.com already exists"));
    }

    @Test
    @DisplayName("POST /auth/register - Should return 400 when email is invalid")
    void register_InvalidEmail_Returns400() throws Exception {
        // GIVEN: Invalid email format
        RegisterRequest invalidRequest = new RegisterRequest();
        invalidRequest.setEmail("not-an-email");
        invalidRequest.setPassword("SecurePass123!");
        invalidRequest.setFullName("Test User");

        // WHEN & THEN: Validation should fail BEFORE reaching service
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())  // 400 Bad Request
                .andExpect(jsonPath("$.errors.email").exists());

        // Service should NOT be called because validation failed
        verify(userService, never()).registerUser(any(), any(), any(), any());
    }

    @Test
    @DisplayName("POST /auth/register - Should return 400 when password is weak")
    void register_WeakPassword_Returns400() throws Exception {
        // GIVEN: Weak password
        RegisterRequest invalidRequest = new RegisterRequest();
        invalidRequest.setEmail("test@example.com");
        invalidRequest.setPassword("weak");
        invalidRequest.setFullName("Test User");

        // WHEN & THEN: Validation should fail
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").exists());

        verify(userService, never()).registerUser(any(), any(), any(), any());
    }

    @Test
    @DisplayName("POST /auth/register - Should return 400 when full name is missing")
    void register_MissingFullName_Returns400() throws Exception {
        // GIVEN: Missing full name
        RegisterRequest invalidRequest = new RegisterRequest();
        invalidRequest.setEmail("test@example.com");
        invalidRequest.setPassword("SecurePass123!");
        invalidRequest.setFullName(null);

        // WHEN & THEN
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.fullName").exists());
    }

    // ============================================
    // LOGIN TESTS
    // ============================================

    @Test
    @DisplayName("POST /auth/login - Should return 200 when credentials are valid")
    void login_Success_Returns200() throws Exception {
        // GIVEN: UserService successfully authenticates
        when(userService.authenticate("test@example.com", "SecurePass123!"))
                .thenReturn(mockUser);

        // WHEN & THEN
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validLoginRequest)))
                .andExpect(status().isOk())  // 200 OK
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Login successful"))
                .andExpect(jsonPath("$.user.email").value("test@example.com"));

        verify(userService, times(1)).authenticate("test@example.com", "SecurePass123!");
    }

    @Test
    @DisplayName("POST /auth/login - Should return 401 when credentials are invalid")
    void login_InvalidCredentials_Returns401() throws Exception {
        // GIVEN: UserService throws exception for invalid credentials
        when(userService.authenticate("test@example.com", "SecurePass123!"))
                .thenThrow(new RuntimeException("Invalid email or password"));

        // WHEN & THEN
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validLoginRequest)))
                .andExpect(status().isUnauthorized())  // 401 Unauthorized
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    @DisplayName("POST /auth/login - Should return 400 when email is missing")
    void login_MissingEmail_Returns400() throws Exception {
        // GIVEN: Login request without email
        LoginRequest invalidRequest = new LoginRequest();
        invalidRequest.setPassword("SecurePass123!");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists());
    }

    // ============================================
    // GET USER BY ID TESTS
    // ============================================

    @Test
    @DisplayName("GET /auth/users/{id} - Should return 200 when user exists")
    void getUserById_Success_Returns200() throws Exception {
        // GIVEN: UserService finds user by ID
        when(userService.findById(1L)).thenReturn(mockUser);

        // WHEN & THEN
        mockMvc.perform(get("/auth/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("test@example.com"))
                .andExpect(jsonPath("$.fullName").value("Test User"));

        verify(userService, times(1)).findById(1L);
    }

    @Test
    @DisplayName("GET /auth/users/{id} - Should return 404 when user doesn't exist")
    void getUserById_NotFound_Returns404() throws Exception {
        // GIVEN: UserService throws exception
        when(userService.findById(999L))
                .thenThrow(new RuntimeException("User not found with ID: 999"));

        // WHEN & THEN
        mockMvc.perform(get("/auth/users/999"))
                .andExpect(status().isNotFound());  // 404 Not Found
    }

    // ============================================
    // CHECK EMAIL TESTS
    // ============================================

    @Test
    @DisplayName("GET /auth/check-email - Should return true when email is available")
    void checkEmail_Available_ReturnsTrue() throws Exception {
        // GIVEN: Email is available (not in use)
        when(userService.isEmailAvailable("new@example.com")).thenReturn(true);

        // WHEN & THEN
        mockMvc.perform(get("/auth/check-email")
                        .param("email", "new@example.com"))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));
    }

    @Test
    @DisplayName("GET /auth/check-email - Should return false when email is taken")
    void checkEmail_Taken_ReturnsFalse() throws Exception {
        // GIVEN: Email is already taken
        when(userService.isEmailAvailable("taken@example.com")).thenReturn(false);

        // WHEN & THEN
        mockMvc.perform(get("/auth/check-email")
                        .param("email", "taken@example.com"))
                .andExpect(status().isOk())
                .andExpect(content().string("false"));
    }
}
