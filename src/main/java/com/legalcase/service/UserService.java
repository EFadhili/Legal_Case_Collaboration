package com.legalcase.service;

import com.legalcase.entity.User;
import com.legalcase.enums.Role;
import com.legalcase.exception.*;
import com.legalcase.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;


import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public User registerUser(String username, String email, String password, String fullName, Role role) {
        log.info("Attempting to register user with username: {}, email: {}", username, email);

        validatePasswordStrength(password);

        if (userRepository.existsByUsername(username)) {
            log.error("Registration failed - username already exists: {}", username);
            throw new DuplicateResourceException("Username", "username", username);
        }

        if (userRepository.existsByEmail(email)) {
            log.error("Registration failed - email already exists: {}", email);
            throw new DuplicateResourceException("User", "email", email);
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setFullName(fullName);
        user.setRole(role != null ? role : Role.STAFF);
        user.setActive(true);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        String encodedPassword = passwordEncoder.encode(password);
        user.setPassword(encodedPassword);

        User savedUser = userRepository.save(user);
        log.info("User registered successfully with ID: {}", savedUser.getId());

        return savedUser;
    }

    @Transactional
    public User registerStaff(String username, String email, String password, String fullName) {
        return registerUser(username, email, password, fullName, Role.STAFF);
    }

    @Transactional
    public User authenticate(String email, String rawPassword) {
        log.debug("Authenticating user: {}", email);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

        if (!user.isActive()) {
            log.warn("Authentication failed - inactive user: {}", email);
            throw new UnauthorizedException("Account is deactivated. Please contact administrator.");
        }

        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            log.warn("Authentication failed - incorrect password for: {}", email);
            throw new UnauthorizedException("Invalid email or password");
        }

        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        log.info("User authenticated successfully: {}", email);
        return user;
    }

    public User findById(Long id) {
        log.debug("Finding user by ID: {}", id);
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
    }

    public User findByEmail(String email) {
        log.debug("Finding user by email: {}", email);
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
    }

    public User findByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));
    }

    public List<User> searchUsers(String searchTerm) {
        if (searchTerm == null || searchTerm.trim().isEmpty()) {
            return List.of();
        }
        return userRepository.findByUsernameOrEmailOrFullName(searchTerm.trim());
    }

    public List<User> autocompleteUsers(String partial) {
        if (partial == null || partial.trim().isEmpty()) {
            return List.of();
        }
        return userRepository.findByUsernameStartingWithOrFullNameStartingWith(partial.trim());
    }

    public List<User> getUsersByRole(Role role) {
        log.debug("Finding users with role: {}", role);
        return userRepository.findByRole(role);
    }

    public List<User> getAllLawyers() {
        return getUsersByRole(Role.LAWYER);
    }

    public List<User> getAllStaff() {
        return getUsersByRole(Role.STAFF);
    }

    public List<User> getActiveUsersByRole(Role role) {
        log.debug("Finding active users with role: {}", role);
        return userRepository.findActiveUsersByRole(role);
    }

    @Transactional
    public void deactivateUser(Long userId, Long adminId) {
        log.info("Admin {} deactivating user: {}", adminId, userId);

        User user = findById(userId);

        if (userId.equals(adminId)) {
            throw new BusinessException("You cannot deactivate your own account");
        }

        user.setActive(false);
        userRepository.save(user);

        log.info("User {} deactivated successfully", userId);
    }

    @Transactional
    public void activateUser(Long userId) {
        log.info("Activating user: {}", userId);

        User user = findById(userId);
        user.setActive(true);
        userRepository.save(user);

        log.info("User {} activated successfully", userId);
    }

    @Transactional
    public void updateLastLogin(Long userId) {
        int updatedCount = userRepository.updateLastLoginTime(userId, LocalDateTime.now());
        if (updatedCount == 0) {
            log.warn("Failed to update last login for user: {}", userId);
        }
    }

    public boolean isEmailAvailable(String email) {
        return !userRepository.existsByEmail(email);
    }

    public boolean isUsernameAvailable(String username) {
        return !userRepository.existsByUsername(username);
    }

    public long getTotalUserCount() {
        return userRepository.count();
    }

    public long countUsersByRole(Role role) {
        return userRepository.countByRole(role);
    }

    private void validatePasswordStrength(String password) {
        if (password == null || password.length() < 8) {
            throw new ValidationException("password", "Password must be at least 8 characters long");
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
            throw new ValidationException("password", "Password must contain at least one uppercase letter");
        }
        if (!hasLowercase) {
            throw new ValidationException("password", "Password must contain at least one lowercase letter");
        }
        if (!hasDigit) {
            throw new ValidationException("password", "Password must contain at least one number");
        }
        if (!hasSpecialChar) {
            throw new ValidationException("password", "Password must contain at least one special character: " + specialChars);
        }
    }

    /**
     * Delete a user by email.
     * Only admins should be able to call this method.
     *
     * @param email Email of the user to delete
     * @param adminId ID of admin performing the action (for audit)
     */
    @Transactional
    public void deleteUserByEmail(String email, Long adminId) {
        log.info("Admin {} deleting user with email: {}", adminId, email);

        User user = findByEmail(email);

        // Prevent deleting yourself
        if (user.getId().equals(adminId)) {
            throw new BusinessException("You cannot delete your own account");
        }

        userRepository.deleteByEmail(email);
        log.info("User with email {} deleted successfully", email);
    }

    /**
     * Find all users with pagination (Admin only).
     */
    public Page<User> findAllUsers(Pageable pageable) {
        log.debug("Finding all users with pagination");
        return userRepository.findAll(pageable);
    }

    /**
     * Delete user by ID (Admin only).
     */
    @Transactional
    public void deleteUserById(Long userId, Long adminId) {
        log.info("Admin {} deleting user with ID: {}", adminId, userId);

        User user = findById(userId);

        // Prevent deleting yourself
        if (user.getId().equals(adminId)) {
            throw new BusinessException("You cannot delete your own account using admin endpoint. Use DELETE /auth/me instead.");
        }

        userRepository.deleteById(userId);
        log.info("User with ID {} deleted successfully", userId);
    }

    /**
     * Update user role (Admin only).
     */
    @Transactional
    public User updateUserRole(Long userId, String roleName) {
        log.info("Updating user {} role to: {}", userId, roleName);

        User user = findById(userId);
        Role newRole;

        try {
            newRole = Role.valueOf(roleName.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ValidationException("role", "Invalid role. Must be ADMIN, LAWYER, or STAFF");
        }

        user.setRole(newRole);
        return userRepository.save(user);
    }

}