package com.legalcase.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class EditMessageRequest {

    @NotBlank(message = "New content is required")
    private String content;  // Changed from newContent

    private String reason;  // Optional reason for edit
}