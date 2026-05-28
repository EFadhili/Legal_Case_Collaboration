package com.legalcase.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.legalcase.enums.CommentType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "comments")
@Data
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CommentType type;

    // Association with Case (for CASE type comments)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id")
    private LegalCase legalCase;

    // Association with Task (for TASK type comments)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id")
    private Task task;

    // Author of the comment
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    // For threaded replies (self-referential relationship)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_comment_id")
    @JsonBackReference
    private Comment parentComment;

    @OneToMany(mappedBy = "parentComment", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonManagedReference
    private List<Comment> replies = new ArrayList<>();

    // Store mentioned user IDs as comma-separated string (e.g., "5,12,23")
    @Column(name = "mentions")
    private String mentions;

    // Soft delete fields
    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by")
    private Long deletedBy;

    @Column(name = "deleted_reason")
    private String deletedReason;

    // Edit tracking fields
    @Column(name = "is_edited", nullable = false)
    private boolean isEdited = false;

    @Column(name = "edited_at")
    private LocalDateTime editedAt;

    @Column(name = "edited_by")
    private Long editedBy;

    @Column(name = "edited_by_name")
    private String editedByName;

    @Column(name = "original_content", columnDefinition = "TEXT")
    private String originalContent;

    @Column(name = "edit_history", columnDefinition = "TEXT")
    private String editHistory;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Helper methods
    public boolean isCaseComment() {
        return type == CommentType.CASE;
    }

    public boolean isTaskComment() {
        return type == CommentType.TASK;
    }

    public boolean hasParent() {
        return parentComment != null;
    }

    public boolean hasMentions() {
        return mentions != null && !mentions.isEmpty();
    }

    /**
     * Parse the mentions string and return list of user IDs.
     * Format stored in database: "1,5,12"
     */
    public List<Long> getMentionedUserIds() {
        if (mentions == null || mentions.isEmpty()) {
            return new ArrayList<>();
        }
        String[] ids = mentions.split(",");
        List<Long> userIds = new ArrayList<>();
        for (String id : ids) {
            try {
                userIds.add(Long.parseLong(id.trim()));
            } catch (NumberFormatException ignored) {
                // Skip invalid entries
            }
        }
        return userIds;
    }

    /**
     * Get display content (handles deleted and edited status)
     */
    public String getDisplayContent() {
        if (isDeleted) {
            return "[This comment was deleted]";
        }

        String displayContent = content;
        if (isEdited) {
            displayContent = displayContent + " (edited)";
        }
        return displayContent;
    }
}