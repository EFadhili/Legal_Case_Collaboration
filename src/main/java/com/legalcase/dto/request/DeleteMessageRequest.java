package com.legalcase.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DeleteMessageRequest {

    @NotNull(message = "Message ID is required")
    private Long messageId;

    private String reason; // Optional reason for deletion
}