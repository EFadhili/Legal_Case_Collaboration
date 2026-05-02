package com.legalcase.entity;

import com.legalcase.enums.CaseMemberRole;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "case_members", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"case_id", "user_id"})
})
@Data
@NoArgsConstructor
public class CaseMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id", nullable = false)
    private LegalCase legalCase;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CaseMemberRole role;

    @Column(name = "joined_at")
    private LocalDateTime joinedAt = LocalDateTime.now();
}

