package com.legalcase.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class MarkMessagesReadRequest {

    @NotNull(message = "Message IDs are required")
    private List<Long> messageIds;  // Keeps Long IDs
}