package com.legalcase.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class ArchiveNotificationsRequest {

    @NotNull(message = "Notification IDs are required")
    private List<Long> notificationIds;
}