package com.legalcase.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legalcase.dto.request.LoginRequest;
import com.legalcase.dto.request.RegisterRequest;
import com.legalcase.entity.User;
import com.legalcase.enums.Role;
import com.legalcase.security.JwtUtils;
import com.legalcase.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Auth Controller Unit Tests")
class AuthControllerUnitTests {

    @Mock
    private UserService userService;

    @Mock
    private JwtUtils jwtUtils;

    @InjectMocks
    private AuthController authController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private RegisterRequest registerRequest;
    private LoginRequest loginRequest;
    private User mockUser;
    private String mockToken;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(authController).build();
        objectMapper = new ObjectMapper();

        // Setup Register Request
        registerRequest = new RegisterRequest();
        registerRequest.setEmail("test@example.com");
        registerRequest.setPassword("SecurePass123!");
        registerRequest.setFullName("Test User");
        registerRequest.setRole(Role.STAFF);

        // Setup Login Request
        loginRequest = new LoginRequest();
        loginRequest.setEmail("test@example.com");
        loginRequest.setPassword("SecurePass123!");

        // Setup Mock User
        mockUser = new User();
        mockUser.setId(1L);
        mockUser.setEmail("test@example.com");
        mockUser.setFullName("Test User");
        mockUser.setRole(Role.STAFF);
        mockUser.setActive(true);
        mockUser.setCreatedAt(LocalDateTime.now());
        mockUser.setUpdatedAt(LocalDateTime.now());

        // Setup Mock Token
        mockToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.mocktoken";
    }

    // ============================================
    // REGISTER TESTS
    // ============================================

    @Test
    @DisplayName("POST /api/auth/register - Should return 201 when registration succeeds")
    void register_Success_Returns201() throws Exception {
        when(userService.registerUser(anyString(), anyString(), anyString(), any(Role.class)))
                .thenReturn(mockUser);
        when(jwtUtils.generateToken(anyLong(), anyString(), anyString()))
                .thenReturn(mockToken);

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("User registered successfully"))
                .andExpect(jsonPath("$.user.email").value("test@example.com"))
                .andExpect(jsonPath("$.user.fullName").value("Test User"))
                .andExpect(jsonPath("$.token").value(mockToken))
                .andExpect(jsonPath("$.user.password").doesNotExist());

        verify(userService, times(1)).registerUser(anyString(), anyString(), anyString(), any(Role.class));
    }

    @Test
    @DisplayName("POST /api/auth/register - Should return 409 when email already exists")
    void register_EmailExists_Returns409() throws Exception {
        when(userService.registerUser(anyString(), anyString(), anyString(), any(Role.class)))
                .thenThrow(new RuntimeException("User with email test@example.com already exists"));

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("User with email test@example.com already exists"));

        verify(userService, times(1)).registerUser(anyString(), anyString(), anyString(), any(Role.class));
    }

    @Test
    @DisplayName("POST /api/auth/register - Should return 400 when email is invalid")
    void register_InvalidEmail_Returns400() throws Exception {
        RegisterRequest invalidRequest = new RegisterRequest();
        invalidRequest.setEmail("not-an-email");
        invalidRequest.setPassword("SecurePass123!");
        invalidRequest.setFullName("Test User");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists());

        verify(userService, never()).registerUser(anyString(), anyString(), anyString(), any(Role.class));
    }

    @Test
    @DisplayName("POST /api/auth/register - Should return 400 when password is weak")
    void register_WeakPassword_Returns400() throws Exception {
        RegisterRequest invalidRequest = new RegisterRequest();
        invalidRequest.setEmail("test@example.com");
        invalidRequest.setPassword("weak");
        invalidRequest.setFullName("Test User");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").exists());

        verify(userService, never()).registerUser(anyString(), anyString(), anyString(), any(Role.class));
    }

    @Test
    @DisplayName("POST /api/auth/register - Should return 400 when full name is missing")
    void register_MissingFullName_Returns400() throws Exception {
        RegisterRequest invalidRequest = new RegisterRequest();
        invalidRequest.setEmail("test@example.com");
        invalidRequest.setPassword("SecurePass123!");
        invalidRequest.setFullName(null);

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
    @DisplayName("POST /api/auth/login - Should return 200 when credentials are valid")
    void login_Success_Returns200() throws Exception {
        when(userService.authenticate(anyString(), anyString())).thenReturn(mockUser);
        when(jwtUtils.generateToken(anyLong(), anyString(), anyString())).thenReturn(mockToken);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Login successful"))
                .andExpect(jsonPath("$.user.email").value("test@example.com"))
                .andExpect(jsonPath("$.token").value(mockToken));

        verify(userService, times(1)).authenticate(anyString(), anyString());
    }

    @Test
    @DisplayName("POST /api/auth/login - Should return 401 when credentials are invalid")
    void login_InvalidCredentials_Returns401() throws Exception {
        when(userService.authenticate(anyString(), anyString()))
                .thenThrow(new RuntimeException("Invalid email or password"));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    @DisplayName("POST /api/auth/login - Should return 400 when email is missing")
    void login_MissingEmail_Returns400() throws Exception {
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
    @DisplayName("GET /api/auth/users/{id} - Should return 200 when user exists")
    void getUserById_Success_Returns200() throws Exception {
        when(userService.findById(1L)).thenReturn(mockUser);

        mockMvc.perform(get("/auth/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("test@example.com"))
                .andExpect(jsonPath("$.fullName").value("Test User"))
                .andExpect(jsonPath("$.password").doesNotExist());

        verify(userService, times(1)).findById(1L);
    }

    @Test
    @DisplayName("GET /api/auth/users/{id} - Should return 404 when user doesn't exist")
    void getUserById_NotFound_Returns404() throws Exception {
        when(userService.findById(999L))
                .thenThrow(new RuntimeException("User not found with ID: 999"));

        mockMvc.perform(get("/auth/users/999"))
                .andExpect(status().isNotFound());
    }

    // ============================================
    // CHECK EMAIL TESTS
    // ============================================

    @Test
    @DisplayName("GET /api/auth/check-email - Should return true when email is available")
    void checkEmail_Available_ReturnsTrue() throws Exception {
        when(userService.isEmailAvailable("new@example.com")).thenReturn(true);

        mockMvc.perform(get("/auth/check-email")
                        .param("email", "new@example.com"))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));
    }

    @Test
    @DisplayName("GET /api/auth/check-email - Should return false when email is taken")
    void checkEmail_Taken_ReturnsFalse() throws Exception {
        when(userService.isEmailAvailable("taken@example.com")).thenReturn(false);

        mockMvc.perform(get("/auth/check-email")
                        .param("email", "taken@example.com"))
                .andExpect(status().isOk())
                .andExpect(content().string("false"));
    }
}
