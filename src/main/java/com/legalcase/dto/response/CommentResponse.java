package com.legalcase.dto.response;

import com.legalcase.entity.Comment;
import com.legalcase.enums.CommentType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Data
@Builder
public class CommentResponse {

    private Long id;
    private String content;
    private CommentType type;

    // Author information
    private Long authorId;
    private String authorUsername;
    private String authorName;
    private String authorEmail;

    // Case information (if case comment)
    private Long caseId;
    private String caseNumber;
    private String caseTitle;

    // Task information (if task comment)
    private Long taskId;
    private String taskTitle;

    // Reply information
    private Long parentCommentId;
    private List<CommentResponse> replies;

    // Mentions
    private List<String> mentionedUsernames;

    // Timestamps
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Permissions (to be set by controller based on user role)
    private boolean canEdit;
    private boolean canDelete;

    // Edit tracking
    private boolean isEdited;
    private LocalDateTime editedAt;
    private Long editedBy;
    private String editedByName;

    // Deletion status (for admins/lawyers)
    private boolean isDeleted;
    private LocalDateTime deletedAt;
    private Long deletedBy;
    private String deletedReason;

    public static CommentResponse fromEntity(Comment comment, String username, boolean isAdmin, boolean isLawyer, Long caseIdForContext) {

        // Safely get case information - check for null before accessing
        Long caseId = null;
        String caseNumber = null;
        String caseTitle = null;

        if (comment.getLegalCase() != null) {
            caseId = comment.getLegalCase().getId();
            caseNumber = comment.getLegalCase().getCaseNumber();
            caseTitle = comment.getLegalCase().getTitle();
        } else if (comment.getTask() != null && comment.getTask().getLegalCase() != null) {
            caseId = comment.getTask().getLegalCase().getId();
            caseNumber = comment.getTask().getLegalCase().getCaseNumber();
            caseTitle = comment.getTask().getLegalCase().getTitle();
        }

        // Safely get task info
        Long taskId = null;
        String taskTitle = null;
        if (comment.getTask() != null) {
            taskId = comment.getTask().getId();
            taskTitle = comment.getTask().getTitle();
        }

        // Safely get parent comment ID
        Long parentCommentId = null;
        if (comment.getParentComment() != null) {
            parentCommentId = comment.getParentComment().getId();
        }

        // Safely get author info (should never be null but check anyway)
        Long authorId = null;
        String authorUsername = null;
        String authorName = null;
        String authorEmail = null;
        if (comment.getAuthor() != null) {
            authorId = comment.getAuthor().getId();
            authorUsername = comment.getAuthor().getUsername();
            authorName = comment.getAuthor().getFullName();
            authorEmail = comment.getAuthor().getEmail();
        }

        // Determine permissions based on user role and authorship
        boolean isAuthor = false;
        if (comment.getAuthor() != null && username != null) {
            isAuthor = comment.getAuthor().getUsername().equals(username);
        }

        boolean canEdit = isAdmin || isLawyer || isAuthor;
        boolean canDelete = isAdmin || isLawyer || isAuthor;

        // For deleted comments, only admins and lawyers can see content
        String displayContent = comment.getDisplayContent();
        if (comment.isDeleted() && !isAdmin && !isLawyer) {
            displayContent = "[This comment was deleted]";
        }

        CommentResponseBuilder builder = CommentResponse.builder()
                .id(comment.getId())
                .content(displayContent)
                .type(comment.getType())
                .authorId(authorId)
                .authorUsername(authorUsername)
                .authorName(authorName)
                .authorEmail(authorEmail)
                .caseId(caseId)
                .caseNumber(caseNumber)
                .caseTitle(caseTitle)
                .taskId(taskId)
                .taskTitle(taskTitle)
                .parentCommentId(parentCommentId)
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt())
                .canEdit(canEdit)
                .canDelete(canDelete)
                .isEdited(comment.isEdited())
                .editedAt(comment.getEditedAt())
                .editedBy(comment.getEditedBy())
                .editedByName(comment.getEditedByName())
                .isDeleted(comment.isDeleted())
                .deletedAt(comment.getDeletedAt())
                .deletedBy(comment.getDeletedBy())
                .deletedReason(comment.getDeletedReason());

        // Add replies if any (only if they are fetched and not null)
        if (comment.getReplies() != null && !comment.getReplies().isEmpty()) {
            builder.replies(comment.getReplies().stream()
                    .filter(reply -> !reply.isDeleted() || isAdmin || isLawyer)
                    .map(reply -> fromEntity(reply, username, isAdmin, isLawyer, caseIdForContext))
                    .collect(Collectors.toList()));
        }

        // Parse mentioned usernames (convert IDs back to usernames for display)
        if (comment.hasMentions()) {
            // For display, we show the IDs as strings (simpler)
            // In production, you might want to fetch actual usernames
            List<String> identifiers = comment.getMentionedUserIds().stream()
                    .map(String::valueOf)
                    .collect(Collectors.toList());
            builder.mentionedUsernames(identifiers);
        } else {
            builder.mentionedUsernames(List.of());
        }

        return builder.build();
    }
}