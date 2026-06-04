package com.legalcase.entity;

import com.legalcase.enums.AuditAction;
import com.legalcase.enums.AuditStatus;
import com.legalcase.enums.EntityType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Who performed the action
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "user_identifier", nullable = false)
    private String userIdentifier;  // username or email

    @Column(name = "user_name")
    private String userName;  // Full name for display

    // What action was performed
    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false)
    private AuditAction action;

    // What type of entity was affected
    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false)
    private EntityType entityType;

    // Which specific entity (ID)
    @Column(name = "entity_id")
    private Long entityId;

    @Column(name = "entity_identifier")
    private String entityIdentifier;  // Human-readable identifier (case number, task number, etc.)

    // Before and after values (for updates)
    @Column(name = "before_value", columnDefinition = "TEXT")
    private String beforeValue;  // JSON representation of state before change

    @Column(name = "after_value", columnDefinition = "TEXT")
    private String afterValue;   // JSON representation of state after change

    // Additional context
    @Column(name = "details", columnDefinition = "TEXT")
    private String details;  // Additional details as JSON

    @Column(name = "ip_address", length = 45)
    private String ipAddress;  // IPv4 or IPv6

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    // Status
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AuditStatus status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;  // If status = FAILURE

    // Timestamp
    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    // Helper method to get display summary
    public String getSummary() {
        return String.format("%s performed %s on %s (ID: %d) at %s",
                userIdentifier, action, entityType, entityId, createdAt);
    }
}