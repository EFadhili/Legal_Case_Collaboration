package com.legalcase.service;

import com.legalcase.entity.User;
import com.legalcase.enums.Role;
import com.legalcase.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service layer for User-related business logic.
 * Handles user registration, authentication, and user management.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Register a new user in the system.
     *
     * Business rules:
     * 1. Email must be unique (no existing user with same email)
     * 2. Password must be encoded before storing
     * 3. New users are active by default
     * 4. Default role is STAFF unless specified otherwise
     *
     * @param email User's email address (used for login)
     * @param password Raw password (will be encoded)
     * @param fullName User's full legal name
     * @param role User's role (ADMIN, LAWYER, STAFF)
     * @return The saved User entity
     * @throws RuntimeException if email already exists
     */
    @Transactional
    public User registerUser(String email, String password, String fullName, Role role) {
        log.info("Attempting to register user with email: {}", email);

        validatePasswordStrength(password);

        // Business rule 1: Check if email already exists
        if (userRepository.existsByEmail(email)) {
            log.error("Registration failed - email already exists: {}", email);
            throw new RuntimeException("User with email " + email + " already exists");
        }

        // Business rule 2: Create new user entity
        User user = new User();
        user.setEmail(email);
        user.setFullName(fullName);
        user.setRole(role != null ? role : Role.STAFF); // Default to STAFF
        user.setActive(true);  // New users are active by default
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        // Business rule 3: Encode password before storing (SECURITY!)
        String encodedPassword = passwordEncoder.encode(password);
        user.setPassword(encodedPassword);

        // Save to database
        User savedUser = userRepository.save(user);
        log.info("User registered successfully with ID: {}", savedUser.getId());

        return savedUser;
    }

    /**
     * Simple registration with default STAFF role.
     */
    @Transactional
    public User registerStaff(String email, String password, String fullName) {
        return registerUser(email, password, fullName, Role.STAFF);
    }

    /**
     * Authenticate a user by email and password.
     *
     * Business rules:
     * 1. User must exist with given email
     * 2. Password must match the stored encoded password
     * 3. User must be active
     *
     * @param email User's email
     * @param rawPassword Plain text password to verify
     * @return The authenticated User
     * @throws RuntimeException if credentials are invalid or user is inactive
     */
    @Transactional
    public User authenticate(String email, String rawPassword) {
        log.debug("Authenticating user: {}", email);

        // Find user by email
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Invalid email or password"));

        // Check if user is active
        if (!user.isActive()) {
            log.warn("Authentication failed - inactive user: {}", email);
            throw new RuntimeException("Account is deactivated. Please contact administrator.");
        }

        // Verify password
        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            log.warn("Authentication failed - incorrect password for: {}", email);
            throw new RuntimeException("Invalid email or password");
        }

        // Update last login time
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        log.info("User authenticated successfully: {}", email);
        return user;
    }

    /**
     * Find a user by their ID.
     *
     * @param id User's unique ID
     * @return The User if found
     * @throws RuntimeException if user not found
     */
    public User findById(Long id) {
        log.debug("Finding user by ID: {}", id);
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + id));
    }

    /**
     * Find a user by their email address.
     *
     * @param email User's email address
     * @return The User if found
     * @throws RuntimeException if user not found
     */
    public User findByEmail(String email) {
        log.debug("Finding user by email: {}", email);
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + email));
    }

    /**
     * Get all users with a specific role.
     *
     * @param role The role to filter by
     * @return List of users (empty list if none found)
     */
    public List<User> getUsersByRole(Role role) {
        log.debug("Finding users with role: {}", role);
        return userRepository.findByRole(role);
    }

    /**
     * Get all lawyers in the system.
     */
    public List<User> getAllLawyers() {
        return getUsersByRole(Role.LAWYER);
    }

    /**
     * Get all staff members (non-lawyer, non-admin).
     */
    public List<User> getAllStaff() {
        return getUsersByRole(Role.STAFF);
    }

    /**
     * Get all active users with a specific role.
     */
    public List<User> getActiveUsersByRole(Role role) {
        log.debug("Finding active users with role: {}", role);
        return userRepository.findActiveUsersByRole(role);
    }

    /**
     * Deactivate a user account.
     * Only ADMIN users should be able to call this method.
     *
     * @param userId ID of user to deactivate
     * @param adminId ID of admin performing the action (for audit)
     */
    @Transactional
    public void deactivateUser(Long userId, Long adminId) {
        log.info("Admin {} deactivating user: {}", adminId, userId);

        User user = findById(userId);

        // Prevent deactivating yourself
        if (userId.equals(adminId)) {
            throw new RuntimeException("You cannot deactivate your own account");
        }

        user.setActive(false);
        userRepository.save(user);

        log.info("User {} deactivated successfully", userId);
    }

    /**
     * Activate a user account.
     */
    @Transactional
    public void activateUser(Long userId) {
        log.info("Activating user: {}", userId);

        User user = findById(userId);
        user.setActive(true);
        userRepository.save(user);

        log.info("User {} activated successfully", userId);
    }

    /**
     * Update user's last login time.
     * Called when user successfully logs in.
     */
    @Transactional
    public void updateLastLogin(Long userId) {
        int updatedCount = userRepository.updateLastLoginTime(userId, LocalDateTime.now());
        if (updatedCount == 0) {
            log.warn("Failed to update last login for user: {}", userId);
        }
    }

    /**
     * Check if an email is already registered.
     */
    public boolean isEmailAvailable(String email) {
        return !userRepository.existsByEmail(email);
    }

    /**
     * Count total users in the system.
     */
    public long getTotalUserCount() {
        return userRepository.count();
    }

    /**
     * Count users by role.
     */
    public long countUsersByRole(Role role) {
        return userRepository.countByRole(role);
    }

    /**
     * Validates password strength without using regex in service.
     * This is an alternative to the regex approach - more readable and maintainable.
     */
    public static void validatePasswordStrength(String password) {
        if (password == null || password.length() < 8) {
            throw new RuntimeException("Password must be at least 8 characters long");
        }

        boolean hasUppercase = false;
        boolean hasLowercase = false;
        boolean hasDigit = false;
        boolean hasSpecialChar = false;

        String specialChars = "@#$%^&+=!";

        for (char c : password.toCharArray()) {
            if (Character.isUpperCase(c)) hasUppercase = true;
            else if (Character.isLowerCase(c)) hasLowercase = true;
            else if (Character.isDigit(c)) hasDigit = true;
            else if (specialChars.indexOf(c) != -1) hasSpecialChar = true;
        }

        if (!hasUppercase) {
            throw new RuntimeException("Password must contain at least one uppercase letter");
        }
        if (!hasLowercase) {
            throw new RuntimeException("Password must contain at least one lowercase letter");
        }
        if (!hasDigit) {
            throw new RuntimeException("Password must contain at least one number");
        }
        if (!hasSpecialChar) {
            throw new RuntimeException("Password must contain at least one special character: " + specialChars);
        }
    }
}