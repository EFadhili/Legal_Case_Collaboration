package com.legalcase.enums;

public enum NotificationPriority {
    LOW,        // Informational (e.g., new message)
    MEDIUM,     // Task assignment, added to case
    HIGH,       // @mention, deadline approaching
    URGENT      // Overdue task
}