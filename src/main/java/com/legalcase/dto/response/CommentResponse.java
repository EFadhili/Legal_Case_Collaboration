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

    // Permissions
    private boolean canEdit;
    private boolean canDelete;

    public static CommentResponse fromEntity(Comment comment, Long currentUserId, boolean isAdmin) {
        CommentResponseBuilder builder = CommentResponse.builder()
                .id(comment.getId())
                .content(comment.getContent())
                .type(comment.getType())
                .authorId(comment.getAuthor().getId())
                .authorUsername(comment.getAuthor().getUsername())
                .authorName(comment.getAuthor().getFullName())
                .authorEmail(comment.getAuthor().getEmail())
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt())
                .canEdit(comment.getAuthor().getId().equals(currentUserId) || isAdmin)
                .canDelete(comment.getAuthor().getId().equals(currentUserId) || isAdmin);

        // Add parent comment info if this is a reply
        if (comment.getParentComment() != null) {
            builder.parentCommentId(comment.getParentComment().getId());
        }

        // Add case info if this is a case comment
        if (comment.getLegalCase() != null) {
            builder.caseId(comment.getLegalCase().getId())
                    .caseNumber(comment.getLegalCase().getCaseNumber())
                    .caseTitle(comment.getLegalCase().getTitle());
        }

        // Add task info if this is a task comment
        if (comment.getTask() != null) {
            builder.taskId(comment.getTask().getId())
                    .taskTitle(comment.getTask().getTitle());
        }

        // Add replies if any
        if (comment.getReplies() != null && !comment.getReplies().isEmpty()) {
            builder.replies(comment.getReplies().stream()
                    .map(reply -> fromEntity(reply, currentUserId, isAdmin))
                    .collect(Collectors.toList()));
        }

        // Parse mentioned usernames
        if (comment.hasMentions()) {
            List<String> usernames = comment.getMentionedUserIds().stream()
                    .map(String::valueOf)
                    .collect(Collectors.toList());
            builder.mentionedUsernames(usernames);
        }

        return builder.build();
    }
}
