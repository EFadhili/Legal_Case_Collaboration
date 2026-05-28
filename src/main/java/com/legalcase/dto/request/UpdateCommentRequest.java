package com.legalcase.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateCommentRequest {

    @NotBlank(message = "Comment content is required")
    private String content;

    private String reason;  // Optional reason for edit
}