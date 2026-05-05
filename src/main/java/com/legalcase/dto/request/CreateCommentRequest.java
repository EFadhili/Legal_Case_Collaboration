package com.legalcase.dto.request;

import com.legalcase.enums.CommentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class CreateCommentRequest {

    @NotBlank(message = "Comment content is required")
    private String content;


    private CommentType type;

    // Required if type = CASE
    private Long caseId;

    // Required if type = TASK
    private Long taskId;

    // Optional: for replies to existing comments
    private Long parentCommentId;

    // Optional: list of mentioned usernames (e.g., ["john", "jane"])
    private List<String> mentionedUsernames;
}

