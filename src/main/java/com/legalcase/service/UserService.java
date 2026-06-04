package com.legalcase.service;

import com.legalcase.entity.User;
import com.legalcase.enums.Role;
import com.legalcase.exception.*;
import com.legalcase.repository.UserRepository;
import com.legalcase.util.AuditContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;  // ADDED

    // ============================================
    // HELPER METHODS
    // ============================================

    private void recordAudit(com.legalcase.enums.AuditAction action,
                             com.legalcase.enums.EntityType entityType,
                             Long entityId, String entityIdentifier,
                             Object beforeState, Object afterState,
                             String details, boolean success, String errorMessage) {
        auditService.recordAuditAsync(
                AuditContext.getCurrentUserId(),
                AuditContext.getCurrentUserIdentifier(),
                AuditContext.getCurrentUserName(),
                action,
                entityType,
                entityId,
                entityIdentifier,
                beforeState,
                afterState,
                details,
                success ? com.legalcase.enums.AuditStatus.SUCCESS : com.legalcase.enums.AuditStatus.FAILURE,
                errorMessage,
                AuditContext.getCurrentIpAddress(),
                AuditContext.getCurrentUserAgent()
        );
    }

    private User findActiveUserById(Long id) {
        return userRepository.findById(id)
                .filter(u -> !u.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
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

    // ============================================
    // REGISTRATION & AUTHENTICATION
    // ============================================

    @Transactional
    public User registerUser(String username, String email, String password, String fullName, Role role) {
        log.info("Attempting to register user with username: {}, email: {}", username, email);

        validatePasswordStrength(password);

        if (userRepository.existsByUsernameAndIsDeletedFalse(username)) {
            log.error("Registration failed - username already exists: {}", username);
            throw new DuplicateResourceException("Username", "username", username);
        }

        if (userRepository.existsByEmailAndIsDeletedFalse(email)) {
            log.error("Registration failed - email already exists: {}", email);
            throw new DuplicateResourceException("User", "email", email);
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setFullName(fullName);
        user.setRole(role != null ? role : Role.STAFF);
        user.setActive(true);
        user.setDeleted(false);

        String encodedPassword = passwordEncoder.encode(password);
        user.setPassword(encodedPassword);

        User savedUser = userRepository.save(user);
        log.info("User registered successfully with ID: {}", savedUser.getId());

        // AUDIT: User registration
        recordAudit(com.legalcase.enums.AuditAction.USER_CREATE,
                com.legalcase.enums.EntityType.USER,
                savedUser.getId(),
                savedUser.getUsername(),
                null,
                savedUser,
                "User registered with role: " + role,
                true,
                null);

        return savedUser;
    }

    @Transactional
    public User registerStaff(String username, String email, String password, String fullName) {
        return registerUser(username, email, password, fullName, Role.STAFF);
    }

    @Transactional
    public User authenticate(String identifier, String rawPassword) {
        log.debug("Authenticating user: {}", identifier);

        try {
            User user = userRepository.findByIdentifierAndIsDeletedFalse(identifier)
                    .orElseThrow(() -> new UnauthorizedException("Invalid username/email or password"));

            if (!user.isActive()) {
                log.warn("Authentication failed - inactive user: {}", identifier);
                // AUDIT: Failed login - inactive account
                recordAudit(com.legalcase.enums.AuditAction.LOGIN_FAILURE,
                        com.legalcase.enums.EntityType.USER,
                        user.getId(),
                        user.getUsername(),
                        null,
                        null,
                        "Login failed: Account is deactivated",
                        false,
                        "Account deactivated");
                throw new UnauthorizedException("Account is deactivated. Please contact administrator.");
            }

            if (user.isDeleted()) {
                log.warn("Authentication failed - deleted user: {}", identifier);
                // AUDIT: Failed login - deleted account
                recordAudit(com.legalcase.enums.AuditAction.LOGIN_FAILURE,
                        com.legalcase.enums.EntityType.USER,
                        user.getId(),
                        user.getUsername(),
                        null,
                        null,
                        "Login failed: Account is deleted",
                        false,
                        "Account deleted");
                throw new UnauthorizedException("Account has been deleted. Please contact administrator.");
            }

            if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
                log.warn("Authentication failed - incorrect password for: {}", identifier);
                // AUDIT: Failed login - wrong password
                recordAudit(com.legalcase.enums.AuditAction.LOGIN_FAILURE,
                        com.legalcase.enums.EntityType.USER,
                        user.getId(),
                        user.getUsername(),
                        null,
                        null,
                        "Login failed: Incorrect password",
                        false,
                        "Invalid credentials");
                throw new UnauthorizedException("Invalid username/email or password");
            }

            user.setLastLoginAt(LocalDateTime.now());
            userRepository.save(user);

            log.info("User authenticated successfully: {}", identifier);

            // test
            auditService.recordAuditSync(
                    user.getId(),
                    user.getEmail(),
                    user.getFullName(),
                    com.legalcase.enums.AuditAction.LOGIN_SUCCESS,
                    com.legalcase.enums.EntityType.USER,
                    user.getId(),
                    user.getUsername(),
                    null,
                    null,
                    "Login test",
                    com.legalcase.enums.AuditStatus.SUCCESS,
                    null,
                    "127.0.0.1",
                    "test"
            );

            // AUDIT: Successful login
            recordAudit(com.legalcase.enums.AuditAction.LOGIN_SUCCESS,
                    com.legalcase.enums.EntityType.USER,
                    user.getId(),
                    user.getUsername(),
                    null,
                    null,
                    "User logged in successfully",
                    true,
                    null);

            return user;

        } catch (UnauthorizedException e) {
            // Re-throw without additional audit (already recorded)
            throw e;
        } catch (Exception e) {
            // AUDIT: Login failure due to system error
            recordAudit(com.legalcase.enums.AuditAction.LOGIN_FAILURE,
                    com.legalcase.enums.EntityType.USER,
                    null,
                    identifier,
                    null,
                    null,
                    "Login failed: System error",
                    false,
                    e.getMessage());
            throw e;
        }
    }

    // ============================================
    // PROFILE MANAGEMENT (User Dashboard)
    // ============================================

    @Transactional
    public User updateProfile(Long userId, String newFullName, String newEmail, Long modifiedBy, String modifiedByName) {
        log.info("User {} updating profile for user {}", modifiedBy, userId);

        User user = findActiveUserById(userId);
        String oldFullName = user.getFullName();
        String oldEmail = user.getEmail();

        // Check if email is being changed and if it's available
        if (!user.getEmail().equals(newEmail)) {
            if (userRepository.existsByEmailAndIsDeletedFalse(newEmail)) {
                throw new DuplicateResourceException("Email", "email", newEmail);
            }
            userRepository.updateEmail(userId, newEmail, modifiedBy, modifiedByName);
        }

        // Update full name
        if (!user.getFullName().equals(newFullName)) {
            userRepository.updateFullName(userId, newFullName, modifiedBy, modifiedByName);
        }

        // Refresh user
        User updatedUser = findActiveUserById(userId);

        // AUDIT: Profile updated
        String details = String.format("Full name: '%s' -> '%s', Email: '%s' -> '%s'",
                oldFullName, newFullName, oldEmail, newEmail);
        recordAudit(com.legalcase.enums.AuditAction.USER_UPDATE,
                com.legalcase.enums.EntityType.USER,
                userId,
                updatedUser.getUsername(),
                null,
                updatedUser,
                details,
                true,
                null);

        return updatedUser;
    }

    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        log.info("User {} changing password", userId);

        User user = findActiveUserById(userId);

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new UnauthorizedException("Current password is incorrect");
        }

        validatePasswordStrength(newPassword);

        String encodedPassword = passwordEncoder.encode(newPassword);
        int updated = userRepository.updatePassword(userId, encodedPassword);

        if (updated == 0) {
            throw new BusinessException("Failed to update password");
        }

        log.info("Password changed successfully for user {}", userId);

        // AUDIT: Password changed
        recordAudit(com.legalcase.enums.AuditAction.PASSWORD_CHANGE,
                com.legalcase.enums.EntityType.USER,
                userId,
                user.getUsername(),
                null,
                null,
                "Password changed by user",
                true,
                null);
    }

    // ============================================
    // FIND METHODS
    // ============================================

    public User findById(Long id) {
        return findActiveUserById(id);
    }

    public User findByEmail(String email) {
        return userRepository.findByEmailAndIsDeletedFalse(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
    }

    public User findByUsername(String username) {
        return userRepository.findByUsernameAndIsDeletedFalse(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));
    }

    public User findByIdentifier(String identifier) {
        return userRepository.findByIdentifierAndIsDeletedFalse(identifier)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username or email", identifier));
    }

    // ============================================
    // SEARCH METHODS
    // ============================================

    public List<User> searchUsers(String searchTerm) {
        if (searchTerm == null || searchTerm.trim().isEmpty()) {
            return List.of();
        }
        return userRepository.findByUsernameOrEmailOrFullNameAndIsDeletedFalse(searchTerm.trim());
    }

    public List<User> autocompleteUsers(String partial) {
        if (partial == null || partial.trim().isEmpty()) {
            return List.of();
        }
        return userRepository.findByUsernameStartingWithOrFullNameStartingWithAndIsDeletedFalse(partial.trim());
    }

    public List<User> getUsersByRole(Role role) {
        return userRepository.findByRoleAndIsDeletedFalse(role);
    }

    public List<User> getAllLawyers() {
        return getUsersByRole(Role.LAWYER);
    }

    public List<User> getAllStaff() {
        return getUsersByRole(Role.STAFF);
    }

    public List<User> getActiveUsersByRole(Role role) {
        return userRepository.findActiveUsersByRole(role);
    }

    public Page<User> findAllActiveUsers(Pageable pageable) {
        return userRepository.findAllActive(pageable);
    }

    // ============================================
    // ADMIN ACTIONS (with audit tracking)
    // ============================================

    @Transactional
    public void softDeleteUser(Long userId, Long adminId, String adminName, String reason) {
        log.info("Admin {} ({}), soft deleting user: {}", adminId, adminName, userId);

        User user = findActiveUserById(userId);

        if (user.getId().equals(adminId)) {
            throw new BusinessException("You cannot delete your own account");
        }

        String deleteReason = (reason != null && !reason.isEmpty()) ? reason : "No reason provided";
        userRepository.softDeleteById(userId, LocalDateTime.now(), adminId, deleteReason);

        log.info("User {} soft deleted by admin {}", userId, adminId);

        // AUDIT: User soft deleted
        recordAudit(com.legalcase.enums.AuditAction.USER_DELETE,
                com.legalcase.enums.EntityType.USER,
                userId,
                user.getUsername(),
                user,
                null,
                "Soft deleted by admin " + adminName + ". Reason: " + deleteReason,
                true,
                null);
    }

    @Transactional
    public void reactivateUser(Long userId, Long adminId, String adminName, String reason) {
        log.info("Admin {} ({}) reactivating user: {}", adminId, adminName, userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        if (!user.isDeleted()) {
            throw new BusinessException("User is not deleted");
        }

        userRepository.reactivateById(userId);
        log.info("User {} reactivated by admin {}", userId, adminId);

        // AUDIT: User reactivated
        recordAudit(com.legalcase.enums.AuditAction.USER_ACTIVATE,
                com.legalcase.enums.EntityType.USER,
                userId,
                user.getUsername(),
                null,
                user,
                "Reactivated by admin " + adminName + (reason != null ? ". Reason: " + reason : ""),
                true,
                null);
    }

    @Transactional
    public void deactivateUser(Long userId, Long adminId, String adminName) {
        log.info("Admin {} ({}) deactivating user: {}", adminId, adminName, userId);

        User user = findActiveUserById(userId);

        if (user.getId().equals(adminId)) {
            throw new BusinessException("You cannot deactivate your own account");
        }

        user.setActive(false);
        user.setLastModifiedBy(adminId);
        user.setLastModifiedByName(adminName);
        userRepository.save(user);

        log.info("User {} deactivated by admin {}", userId, adminId);

        // AUDIT: User deactivated
        recordAudit(com.legalcase.enums.AuditAction.USER_DEACTIVATE,
                com.legalcase.enums.EntityType.USER,
                userId,
                user.getUsername(),
                null,
                user,
                "Deactivated by admin " + adminName,
                true,
                null);
    }

    @Transactional
    public void activateUser(Long userId, Long adminId, String adminName) {
        log.info("Admin {} ({}) activating user: {}", adminId, adminName, userId);

        User user = findActiveUserById(userId);

        user.setActive(true);
        user.setLastModifiedBy(adminId);
        user.setLastModifiedByName(adminName);
        userRepository.save(user);

        log.info("User {} activated by admin {}", userId, adminId);

        // AUDIT: User activated
        recordAudit(com.legalcase.enums.AuditAction.USER_ACTIVATE,
                com.legalcase.enums.EntityType.USER,
                userId,
                user.getUsername(),
                null,
                user,
                "Activated by admin " + adminName,
                true,
                null);
    }

    @Transactional
    public User updateUserRole(Long userId, String roleName, Long adminId, String adminName) {
        log.info("Admin {} ({}) updating user {} role to: {}", adminId, adminName, userId, roleName);

        User user = findActiveUserById(userId);
        Role oldRole = user.getRole();
        Role newRole;

        try {
            newRole = Role.valueOf(roleName.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ValidationException("role", "Invalid role. Must be ADMIN, LAWYER, or STAFF");
        }

        user.setRole(newRole);
        user.setLastModifiedBy(adminId);
        user.setLastModifiedByName(adminName);

        User updatedUser = userRepository.save(user);
        log.info("User {} role updated to {} by admin {}", userId, newRole, adminId);

        // AUDIT: User role changed
        recordAudit(com.legalcase.enums.AuditAction.USER_ROLE_CHANGE,
                com.legalcase.enums.EntityType.USER,
                userId,
                user.getUsername(),
                oldRole,
                newRole,
                "Role changed from " + oldRole + " to " + newRole + " by admin " + adminName,
                true,
                null);

        return updatedUser;
    }

    @Transactional
    public void permanentlyDeleteUser(Long userId, Long adminId, String adminName) {
        log.info("Admin {} ({}) permanently deleting user: {}", adminId, adminName, userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        if (user.getId().equals(adminId)) {
            throw new BusinessException("You cannot permanently delete your own account");
        }

        userRepository.deleteById(userId);
        log.info("User {} permanently deleted by admin {}", userId, adminId);

        // AUDIT: User permanently deleted
        recordAudit(com.legalcase.enums.AuditAction.USER_DELETE,
                com.legalcase.enums.EntityType.USER,
                userId,
                user.getUsername(),
                user,
                null,
                "Permanently deleted by admin " + adminName,
                true,
                null);
    }

    // ============================================
    // STATISTICS
    // ============================================

    public long getTotalActiveUserCount() {
        return userRepository.countActiveUsers();
    }

    public long countUsersByRole(Role role) {
        return userRepository.countByRoleAndIsDeletedFalse(role);
    }

    public boolean isEmailAvailable(String email) {
        return !userRepository.existsByEmailAndIsDeletedFalse(email);
    }

    public boolean isUsernameAvailable(String username) {
        return !userRepository.existsByUsernameAndIsDeletedFalse(username);
    }

    @Transactional
    public void updateLastLogin(Long userId) {
        userRepository.updateLastLoginTime(userId, LocalDateTime.now());
    }

    // ============================================
    // CLEANUP SCHEDULER METHOD
    // ============================================

    @Transactional
    public int permanentlyDeleteSoftDeletedUsersOlderThan(int days) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(days);
        List<User> usersToDelete = userRepository.findSoftDeletedUsersOlderThan(cutoffDate);

        int deletedCount = 0;
        for (User user : usersToDelete) {
            userRepository.deleteById(user.getId());
            deletedCount++;
            log.info("Permanently deleted soft-deleted user: {} (ID: {})", user.getUsername(), user.getId());

            // AUDIT: System cleanup - user permanently deleted
            recordAudit(com.legalcase.enums.AuditAction.USER_DELETE,
                    com.legalcase.enums.EntityType.USER,
                    user.getId(),
                    user.getUsername(),
                    user,
                    null,
                    "System cleanup: Permanently deleted soft-deleted user older than " + days + " days",
                    true,
                    null);
        }

        return deletedCount;
    }
}