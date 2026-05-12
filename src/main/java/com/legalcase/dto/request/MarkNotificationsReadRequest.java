package com.legalcase.dto.request;

import lombok.Data;

import java.util.List;

@Data
public class MarkNotificationsReadRequest {
    private List<Long> notificationIds;
}