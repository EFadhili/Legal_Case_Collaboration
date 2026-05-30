package com.legalcase.enums;

public enum NotificationType {
    // ===== User-related notifications =====
    ADDED_TO_CASE,              // User added to a case
    REMOVED_FROM_CASE,          // User removed from a case

    // ===== Task-related notifications =====
    TASK_ASSIGNED,              // User assigned to a task
    TASK_DEADLINE_APPROACHING,  // Task due date approaching
    TASK_OVERDUE,               // Task past due date
    TASK_COMPLETED,             // Task marked as completed
    TASK_DEPENDENCY_MET,        // Dependent task becomes available
    TASK_MENTIONED,             // Task mentioned in chat

    // ===== Case-related notifications =====
    CASE_DEADLINE_APPROACHING,  // Case due date approaching

    // ===== Chat-related notifications =====
    USER_MENTIONED,             // User @mentioned in chat
    NEW_CHAT_MESSAGE,           // New message in case chat

    // ===== Comment-related notifications =====
    USER_MENTIONED_IN_COMMENT,  // User @mentioned in a comment
    COMMENT_REPLY,              // Someone replied to user's comment
    NEW_CASE_COMMENT,           // New comment on case
    NEW_TASK_COMMENT,           // New comment on task

    // ===== Document-related notifications =====
    DOCUMENT_UPLOADED,          // Document uploaded to case/task
    DOCUMENT_PROCESSED,         // Text extraction complete (success or failure)

    // ===== AI-related notifications =====
    AI_ANALYSIS_COMPLETE        // Long AI query finished processing
}