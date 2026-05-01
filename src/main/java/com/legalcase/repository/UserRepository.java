package com.legalcase.repository;

import com.legalcase.entity.User;
import com.legalcase.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for User entity operations.
 * Spring Data JPA automatically implements all methods defined here.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // ===== BASIC FIND METHODS (Spring implements by method name) =====

    /**
     * Find a user by their email address.
     * Spring generates: SELECT u FROM User u WHERE u.email = ?1
     */
    Optional<User> findByEmail(String email);

    /**
     * Check if a user exists with the given email.
     * Spring generates: SELECT COUNT(u) > 0 FROM User u WHERE u.email = ?1
     */
    boolean existsByEmail(String email);

    /**
     * Find all users with a specific role.
     * Spring generates: SELECT u FROM User u WHERE u.role = ?1
     */
    List<User> findByRole(Role role);

    /**
     * Find users whose full name contains the given text (case-insensitive).
     * Spring generates: SELECT u FROM User u WHERE LOWER(u.fullName) LIKE LOWER(CONCAT('%', ?1, '%'))
     */
    List<User> findByFullNameContainingIgnoreCase(String namePart);

    /**
     * Find users who have logged in after a specific date.
     * Spring generates: SELECT u FROM User u WHERE u.lastLoginAt > ?1
     */
    List<User> findByLastLoginAtAfter(LocalDateTime date);

    /**
     * Count how many users have a specific role.
     * Spring generates: SELECT COUNT(u) FROM User u WHERE u.role = ?1
     */
    long countByRole(Role role);

    // ===== CUSTOM QUERIES (using @Query when method name isn't enough) =====

    /**
     * Find active users (isActive = true) with a specific role.
     * Using @Query because the condition combines two fields.
     */
    @Query("SELECT u FROM User u WHERE u.isActive = true AND u.role = :role")
    List<User> findActiveUsersByRole(@Param("role") Role role);

    /**
     * Find users who haven't logged in for a certain number of days.
     * Using native SQL for date arithmetic that differs between databases.
     */
    @Query(value = "SELECT * FROM users WHERE last_login_at < :cutoffDate", nativeQuery = true)
    List<User> findInactiveUsersSince(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Update a user's last login time.
     * Using @Modifying for UPDATE operations.
     */
    @Query("UPDATE User u SET u.lastLoginAt = :loginTime WHERE u.id = :userId")
    int updateLastLoginTime(@Param("userId") Long userId, @Param("loginTime") LocalDateTime loginTime);

    // ===== DELETE METHODS =====

    /**
     * Delete a user by email.
     * Spring generates: DELETE FROM User u WHERE u.email = ?1
     */
    void deleteByEmail(String email);
}