package com.legalcase.entity;

import com.legalcase.enums.Role;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * User entity representing a person who can log into the system.
 * Each user has a role (ADMIN, LAWYER, STAFF) that determines their permissions.
 */
@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class User {

    // ===== PRIMARY KEY =====

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ===== AUTHENTICATION FIELDS =====

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Role role;

    // ===== ACCOUNT STATUS FIELDS =====

    @Column(name = "is_active")
    private boolean isActive = true;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    // ===== AUDIT FIELDS =====

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ===== HELPER METHODS =====

    /**
     * Checks if this user has administrative privileges.
     * @return true if user role is ADMIN
     */
    public boolean isAdmin() {
        return this.role == Role.ADMIN;
    }

    /**
     * Checks if this user is a lawyer.
     * @return true if user role is LAWYER
     */
    public boolean isLawyer() {
        return this.role == Role.LAWYER;
    }

    /**
     * Checks if this user is staff.
     * @return true if user role is STAFF
     */
    public boolean isStaff() {
        return this.role == Role.STAFF;
    }
}