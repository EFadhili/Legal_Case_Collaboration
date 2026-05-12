package com.legalcase.enums;

public enum NotificationType {
    // User-related notifications
    ADDED_TO_CASE,          // User added to a case
    REMOVED_FROM_CASE,      // User removed from a case

    // Task-related notifications
    TASK_ASSIGNED,          // User assigned to a task
    TASK_DEADLINE_APPROACHING,  // Task due date approaching
    TASK_OVERDUE,           // Task past due date
    TASK_MENTIONED,         // Task mentioned in chat

    // Case-related notifications
    CASE_DEADLINE_APPROACHING,  // Case due date approaching

    // Chat-related notifications
    USER_MENTIONED,         // User @mentioned in chat
    NEW_CHAT_MESSAGE        // New message in case chat (for other members)
}