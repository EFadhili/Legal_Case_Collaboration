package com.legalcase.entity;

import com.legalcase.enums.DocumentStatus;
import com.legalcase.enums.TextExtractionStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "documents")
@Data
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // File metadata
    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "original_file_name", nullable = false)
    private String originalFileName;

    @Column(name = "file_type", nullable = false)
    private String fileType;

    @Column(name = "file_extension", nullable = false)
    private String fileExtension;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "mime_type")
    private String mimeType;

    // Storage information
    @Column(name = "storage_path", nullable = false)
    private String storagePath;

    @Column(name = "storage_bucket")
    private String storageBucket;

    // Associations (exactly one of these is non-null)
    @Column(name = "case_id")
    private Long caseId;

    @Column(name = "task_id")
    private Long taskId;

    // Upload information
    @Column(name = "uploaded_by_id", nullable = false)
    private Long uploadedById;

    @CreatedDate
    @Column(name = "uploaded_at", updatable = false)
    private LocalDateTime uploadedAt;

    // Extracted text
    @Column(name = "extracted_text", columnDefinition = "TEXT")
    private String extractedText;

    @Enumerated(EnumType.STRING)
    @Column(name = "text_extraction_status")
    private TextExtractionStatus textExtractionStatus = TextExtractionStatus.PENDING;

    @Column(name = "text_extraction_error")
    private String textExtractionError;

    @Column(name = "processing_progress")
    private Integer processingProgress = 0;

    @Column(name = "processing_retry_count")
    private Integer processingRetryCount = 0;

    @Column(name = "processing_started_at")
    private LocalDateTime processingStartedAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    // Versioning
    private Integer version = 1;

    @Column(name = "parent_document_id")
    private Long parentDocumentId;

    @Column(name = "is_latest")
    private boolean isLatest = true;

    // Metadata
    private String description;
    private String tags;

    // Status
    @Enumerated(EnumType.STRING)
    private DocumentStatus status = DocumentStatus.ACTIVE;

    @Column(name = "is_deleted")
    private boolean isDeleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // Audit
    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Helper methods
    public String getReadableFileSize() {
        if (fileSize == null) return "0 B";
        String[] units = {"B", "KB", "MB", "GB"};
        int unitIndex = 0;
        double size = fileSize.doubleValue();
        while (size >= 1024 && unitIndex < units.length - 1) {
            size /= 1024;
            unitIndex++;
        }
        return String.format("%.1f %s", size, units[unitIndex]);
    }

    public String getExtractedTextPreview(int length) {
        if (extractedText == null) return "";
        return extractedText.length() > length ? extractedText.substring(0, length) + "..." : extractedText;
    }

    public boolean isCaseDocument() {
        return caseId != null;
    }

    public boolean isTaskDocument() {
        return taskId != null;
    }
}