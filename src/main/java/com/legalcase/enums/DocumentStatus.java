package com.legalcase.enums;

public enum DocumentStatus {
    ACTIVE,      // Normal, available
    DELETED,     // Soft deleted
    PROCESSING,  // Text extraction in progress
    FAILED       // Extraction failed or upload error
}