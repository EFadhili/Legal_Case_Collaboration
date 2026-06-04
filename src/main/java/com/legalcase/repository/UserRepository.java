package com.legalcase.repository;

import com.legalcase.entity.User;
import com.legalcase.enums.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // ===== FIND METHODS (excluding soft-deleted users) =====

    Optional<User> findByEmailAndIsDeletedFalse(String email);

    Optional<User> findByUsernameAndIsDeletedFalse(String username);

    @Query("SELECT u FROM User u WHERE (u.email = :identifier OR u.username = :identifier) AND u.isDeleted = false")
    Optional<User> findByIdentifierAndIsDeletedFalse(@Param("identifier") String identifier);

    boolean existsByEmailAndIsDeletedFalse(String email);

    boolean existsByUsernameAndIsDeletedFalse(String username);

    List<User> findByRoleAndIsDeletedFalse(Role role);

    @Query("SELECT u FROM User u WHERE u.isActive = true AND u.role = :role AND u.isDeleted = false")
    List<User> findActiveUsersByRole(@Param("role") Role role);

    @Query("SELECT u FROM User u WHERE u.username = :searchTerm OR u.email = :searchTerm OR u.fullName = :searchTerm AND u.isDeleted = false")
    List<User> findByUsernameOrEmailOrFullNameAndIsDeletedFalse(@Param("searchTerm") String searchTerm);

    @Query("""
        SELECT u FROM User u
        WHERE u.isDeleted = false AND (
            LOWER(u.username) LIKE LOWER(CONCAT('%', :partial, '%'))
            OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :partial, '%'))
            OR LOWER(u.email) LIKE LOWER(CONCAT('%', :partial, '%'))
        )
    """)
    List<User> findByUsernameStartingWithOrFullNameStartingWithAndIsDeletedFalse(@Param("partial") String partial);

    @Query("SELECT u FROM User u WHERE u.isDeleted = false")
    Page<User> findAllActive(Pageable pageable);

    @Query("SELECT u FROM User u WHERE u.isDeleted = true AND u.deletedAt < :cutoffDate")
    List<User> findSoftDeletedUsersOlderThan(@Param("cutoffDate") LocalDateTime cutoffDate);

    @Query("SELECT u FROM User u WHERE u.isDeleted = false AND u.isActive = true")
    List<User> findAllAccessible();

    // ===== UPDATE METHODS =====

    @Modifying
    @Query("UPDATE User u SET u.lastLoginAt = :loginTime WHERE u.id = :userId")
    int updateLastLoginTime(@Param("userId") Long userId, @Param("loginTime") LocalDateTime loginTime);

    @Modifying
    @Query("UPDATE User u SET u.password = :newPassword WHERE u.id = :userId AND u.isDeleted = false")
    int updatePassword(@Param("userId") Long userId, @Param("newPassword") String newPassword);

    @Modifying
    @Query("UPDATE User u SET u.email = :newEmail, u.lastModifiedBy = :modifiedBy, u.lastModifiedByName = :modifiedByName WHERE u.id = :userId AND u.isDeleted = false")
    int updateEmail(@Param("userId") Long userId, @Param("newEmail") String newEmail,
                    @Param("modifiedBy") Long modifiedBy, @Param("modifiedByName") String modifiedByName);

    @Modifying
    @Query("UPDATE User u SET u.fullName = :fullName, u.lastModifiedBy = :modifiedBy, u.lastModifiedByName = :modifiedByName WHERE u.id = :userId AND u.isDeleted = false")
    int updateFullName(@Param("userId") Long userId, @Param("fullName") String fullName,
                       @Param("modifiedBy") Long modifiedBy, @Param("modifiedByName") String modifiedByName);

    // ===== SOFT DELETE METHODS =====

    @Modifying
    @Query("UPDATE User u SET u.isDeleted = true, u.deletedAt = :deletedAt, u.deletedBy = :deletedBy, u.deletedReason = :deletedReason, u.isActive = false WHERE u.id = :userId")
    void softDeleteById(@Param("userId") Long userId, @Param("deletedAt") LocalDateTime deletedAt,
                        @Param("deletedBy") Long deletedBy, @Param("deletedReason") String deletedReason);

    @Modifying
    @Query("UPDATE User u SET u.isDeleted = false, u.deletedAt = null, u.deletedBy = null, u.deletedReason = null, u.isActive = true WHERE u.id = :userId")
    void reactivateById(@Param("userId") Long userId);

    // ===== COUNT METHODS =====

    long countByRoleAndIsDeletedFalse(Role role);

    @Query("SELECT COUNT(u) FROM User u WHERE u.isDeleted = false")
    long countActiveUsers();

    // ===== LEGACY METHODS (deprecated - use new ones) =====

    @Deprecated
    Optional<User> findByEmail(String email);

    @Deprecated
    Optional<User> findByUsername(String username);

    @Deprecated
    boolean existsByEmail(String email);

    @Deprecated
    boolean existsByUsername(String username);

    @Deprecated
    List<User> findByRole(Role role);

    @Deprecated
    void deleteByEmail(String email);
}