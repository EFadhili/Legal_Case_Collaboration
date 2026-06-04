package com.legalcase.entity;

import com.legalcase.enums.Role;
import jakarta.persistence.*;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ===== AUTHENTICATION FIELDS =====
    @Column(nullable = false, unique = true)
    @Size(min = 3, max = 20, message = "Username must be between 3 and 20 characters")
    @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "Username can only contain letters, numbers, dots, underscores, and hyphens")
    private String username;

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

    // ===== SOFT DELETE FIELDS =====
    @Column(name = "is_deleted")
    private boolean isDeleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by")
    private Long deletedBy;

    @Column(name = "deleted_reason")
    private String deletedReason;

    // ===== EDIT TRACKING FIELDS =====
    @Column(name = "last_modified_by")
    private Long lastModifiedBy;

    @Column(name = "last_modified_by_name")
    private String lastModifiedByName;

    // ===== AUDIT FIELDS =====
    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ===== HELPER METHODS =====

    public boolean isAdmin() {
        return this.role == Role.ADMIN;
    }

    public boolean isLawyer() {
        return this.role == Role.LAWYER;
    }

    public boolean isStaff() {
        return this.role == Role.STAFF;
    }

    /**
     * Get display name for UI (handles deleted users)
     */
    public String getDisplayName() {
        if (isDeleted) {
            return "[Deleted User] " + (fullName != null ? fullName : username);
        }
        return fullName != null ? fullName : username;
    }

    /**
     * Check if user account is accessible (active and not deleted)
     */
    public boolean isAccessible() {
        return isActive && !isDeleted;
    }
}