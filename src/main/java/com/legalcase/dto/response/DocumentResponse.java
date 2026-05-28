package com.legalcase.dto.response;

import com.legalcase.entity.Document;
import com.legalcase.enums.DocumentStatus;
import com.legalcase.enums.TextExtractionStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class DocumentResponse {

    private Long id;
    private String documentNumber;
    private String fileName;
    private String originalFileName;
    private String fileType;
    private String fileExtension;
    private Long fileSize;
    private String readableFileSize;
    private String mimeType;

    // Association info
    private Long caseId;
    private String caseNumber;
    private String caseTitle;
    private Long taskId;
    private String taskTitle;

    // Uploader info
    private Long uploadedById;
    private String uploadedBy;
    private String uploadedByUsername;
    private LocalDateTime uploadedAt;

    // Extracted text
    private String extractedTextPreview;
    private TextExtractionStatus textExtractionStatus;
    private Integer processingProgress;

    // Versioning
    private Integer version;
    private boolean isLatest;
    private Long parentDocumentId;

    // Metadata
    private String description;
    private String tags;

    // Status
    private DocumentStatus status;
    private boolean isDeleted;
    private LocalDateTime deletedAt;

    // Edit tracking
    private boolean isEdited;
    private Long lastEditedById;
    private String lastEditedByName;
    private LocalDateTime lastEditedAt;

    // Download URL
    private String downloadUrl;

    // Timestamps
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static DocumentResponse fromEntity(Document document, String downloadUrl) {
        DocumentResponseBuilder builder = DocumentResponse.builder()
                .id(document.getId())
                .documentNumber(document.getDocumentNumber())
                .fileName(document.getFileName())
                .originalFileName(document.getOriginalFileName())
                .fileType(document.getFileType())
                .fileExtension(document.getFileExtension())
                .fileSize(document.getFileSize())
                .readableFileSize(document.getReadableFileSize())
                .mimeType(document.getMimeType())
                .extractedTextPreview(document.getExtractedTextPreview(500))
                .textExtractionStatus(document.getTextExtractionStatus())
                .processingProgress(document.getProcessingProgress())
                .version(document.getVersion())
                .isLatest(document.isLatest())
                .description(document.getDescription())
                .tags(document.getTags())
                .status(document.getStatus())
                .isDeleted(document.isDeleted())
                .deletedAt(document.getDeletedAt())
                .isEdited(document.isEdited())
                .lastEditedById(document.getLastEditedById())
                .lastEditedByName(document.getLastEditedByName())
                .lastEditedAt(document.getLastEditedAt())
                .downloadUrl(downloadUrl)
                .createdAt(document.getCreatedAt())
                .updatedAt(document.getUpdatedAt());

        // Case information
        if (document.getLegalCase() != null) {
            builder.caseId(document.getLegalCase().getId())
                    .caseNumber(document.getLegalCase().getCaseNumber())
                    .caseTitle(document.getLegalCase().getTitle());
        }

        // Task information
        if (document.getTask() != null) {
            builder.taskId(document.getTask().getId())
                    .taskTitle(document.getTask().getTitle());
        }

        // Uploader information
        if (document.getUploadedBy() != null) {
            builder.uploadedById(document.getUploadedBy().getId())
                    .uploadedBy(document.getUploadedBy().getFullName())
                    .uploadedByUsername(document.getUploadedBy().getUsername())
                    .uploadedAt(document.getUploadedAt());
        }

        // Parent document info
        if (document.getParentDocument() != null) {
            builder.parentDocumentId(document.getParentDocument().getId());
        }

        return builder.build();
    }
}