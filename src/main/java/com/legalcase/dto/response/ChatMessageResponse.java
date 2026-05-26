package com.legalcase.dto.response;

import com.legalcase.entity.ChatMessage;
import com.legalcase.enums.MessageType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ChatMessageResponse {

    private Long id;
    private String content;
    private MessageType type;
    private Long caseId;
    private String caseNumber;
    private String caseTitle;
    private Long senderId;
    private String senderUsername;
    private String senderName;
    private String fileUrl;
    private String fileName;
    private Long fileSize;
    private List<Long> mentionedUserIds;
    private List<Long> mentionedTaskIds;
    private LocalDateTime sentAt;
    private boolean isRead;
    private LocalDateTime readAt;

    // NEW: Deletion fields
    private boolean isDeleted;
    private LocalDateTime deletedAt;
    private Long deletedBy;
    private String deletedReason;

    // NEW: Edit fields
    private boolean isEdited;
    private LocalDateTime editedAt;
    private Long editedBy;
    private String editedByName;

    private static String formatContent(ChatMessage message) {
        if (message.isDeleted()) {
            return "[This message was deleted]";
        }

        String content = message.getContent();
        if (message.isEdited()) {
            content = content + " (edited)";
        }
        return content;
    }

    public static ChatMessageResponse fromEntity(ChatMessage message) {
        ChatMessageResponseBuilder builder = ChatMessageResponse.builder()
                .id(message.getId())
                .content(formatContent(message))
                .type(message.getType())
                .caseId(message.getLegalCase().getId())
                .caseNumber(message.getLegalCase().getCaseNumber())
                .caseTitle(message.getLegalCase().getTitle())
                .senderId(message.getSender().getId())
                .senderUsername(message.getSender().getUsername())
                .senderName(message.getSender().getFullName())
                .mentionedUserIds(message.getMentionedUserIdsAsList())
                .mentionedTaskIds(message.getMentionedTaskIdsAsList())
                .sentAt(message.getSentAt())
                .isRead(message.isRead())
                .readAt(message.getReadAt())
                .isDeleted(message.isDeleted())
                .deletedAt(message.getDeletedAt())
                .deletedBy(message.getDeletedBy())
                .deletedReason(message.getDeletedReason())
                .isEdited(message.isEdited())
                .editedAt(message.getEditedAt())
                .editedBy(message.getEditedBy())
                .editedByName(message.getEditedByName());

        if (message.getFileUrl() != null && !message.isDeleted()) {
            builder.fileUrl(message.getFileUrl())
                    .fileName(message.getFileName())
                    .fileSize(message.getFileSize());
        }

        return builder.build();
    }
}