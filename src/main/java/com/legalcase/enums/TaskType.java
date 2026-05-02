package com.legalcase.enums;

public enum TaskType {
    MANDATORY,  // Must be completed for case to close
    OPTIONAL,   // Not required for case closure
    REVIEW      // Requires approval from lawyer
}