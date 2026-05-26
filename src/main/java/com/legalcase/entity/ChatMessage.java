package com.legalcase.entity;

import com.legalcase.enums.MessageType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "chat_messages")
@Data
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageType type = MessageType.TEXT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id", nullable = false)
    private LegalCase legalCase;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @Column(name = "file_url")
    private String fileUrl;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "file_size")
    private Long fileSize;

    // Store mentioned user IDs as comma-separated string (e.g., "5,12,23")
    @Column(name = "mentioned_user_ids")
    private String mentionedUserIds;

    // Store mentioned task IDs as comma-separated string (e.g., "5,12,23")
    @Column(name = "mentioned_task_ids")
    private String mentionedTaskIds;

    // NEW: Soft delete fields for message deletion feature
    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by")
    private Long deletedBy;

    @Column(name = "deleted_reason")
    private String deletedReason;

    @CreatedDate
    @Column(name = "sent_at", updatable = false)
    private LocalDateTime sentAt;

    @Column(name = "is_edited", nullable = false)
    private boolean isEdited = false;

    @Column(name = "edited_at")
    private LocalDateTime editedAt;

    @Column(name = "edited_by")
    private Long editedBy;

    @Column(name = "edited_by_name")
    private String editedByName;

    @Column(name = "original_content", columnDefinition = "TEXT")
    private String originalContent;  // Store original for audit trail

    @Column(name = "edit_history", columnDefinition = "TEXT")
    private String editHistory;  // JSON string storing edit history

    @Column(name = "is_read")
    private boolean isRead = false;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    // Helper methods
    public List<Long> getMentionedUserIdsAsList() {
        if (mentionedUserIds == null || mentionedUserIds.isEmpty()) {
            return new ArrayList<>();
        }
        return parseCommaSeparatedIds(mentionedUserIds);
    }

    public List<Long> getMentionedTaskIdsAsList() {
        if (mentionedTaskIds == null || mentionedTaskIds.isEmpty()) {
            return new ArrayList<>();
        }
        return parseCommaSeparatedIds(mentionedTaskIds);
    }

    private List<Long> parseCommaSeparatedIds(String ids) {
        String[] idArray = ids.split(",");
        List<Long> result = new ArrayList<>();
        for (String id : idArray) {
            try {
                result.add(Long.parseLong(id.trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        return result;
    }
}