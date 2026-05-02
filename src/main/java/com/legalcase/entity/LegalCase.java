package com.legalcase.entity;

import com.legalcase.enums.CasePriority;
import com.legalcase.enums.CaseStatus;
import com.legalcase.enums.CaseType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "legal_cases")
@Data
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class LegalCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String caseNumber;

    @Column(nullable = false)
    private String title;

    @Column(length = 5000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CaseStatus status = CaseStatus.OPEN;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CasePriority priority = CasePriority.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CaseType type;

    private LocalDate dueDate;

    private LocalDate filingDate;

    private LocalDate hearingDate;

    @Column(name = "is_locked")
    private boolean isLocked = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Helper methods for transitions
    public boolean canTransitionTo(CaseStatus newStatus) {
        switch (this.status) {
            case OPEN:
                return newStatus == CaseStatus.IN_PROGRESS;
            case IN_PROGRESS:
                return newStatus == CaseStatus.CLOSED;
            case CLOSED:
                return newStatus == CaseStatus.ARCHIVED;
            case ARCHIVED:
                return false;
            default:
                return false;
        }
    }

    public boolean isAvailableForWork() {
        return !isLocked && (status == CaseStatus.OPEN || status == CaseStatus.IN_PROGRESS);
    }
}

