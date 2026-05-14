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
    private String fileName;
    private String originalFileName;
    private String fileType;
    private String fileExtension;
    private Long fileSize;
    private String readableFileSize;
    private String mimeType;
    private Long caseId;
    private Long taskId;
    private Long uploadedById;
    private String uploadedBy;
    private String uploadedByUsername;
    private LocalDateTime uploadedAt;
    private String extractedTextPreview;
    private TextExtractionStatus textExtractionStatus;
    private Integer processingProgress;
    private Integer version;
    private boolean isLatest;
    private String description;
    private String tags;
    private DocumentStatus status;
    private String downloadUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static DocumentResponse fromEntity(Document document, String downloadUrl, String uploadedByName, String uploadedByUsername) {
        DocumentResponseBuilder builder = DocumentResponse.builder()
                .id(document.getId())
                .fileName(document.getFileName())
                .originalFileName(document.getOriginalFileName())
                .fileType(document.getFileType())
                .fileExtension(document.getFileExtension())
                .fileSize(document.getFileSize())
                .readableFileSize(document.getReadableFileSize())
                .mimeType(document.getMimeType())
                .caseId(document.getCaseId())
                .taskId(document.getTaskId())
                .uploadedById(document.getUploadedById())
                .uploadedBy(uploadedByName)
                .uploadedByUsername(uploadedByUsername)
                .uploadedAt(document.getUploadedAt())
                .extractedTextPreview(document.getExtractedTextPreview(500))
                .textExtractionStatus(document.getTextExtractionStatus())
                .processingProgress(document.getProcessingProgress())
                .version(document.getVersion())
                .isLatest(document.isLatest())
                .description(document.getDescription())
                .tags(document.getTags())
                .status(document.getStatus())
                .downloadUrl(downloadUrl)
                .createdAt(document.getCreatedAt())
                .updatedAt(document.getUpdatedAt());

        return builder.build();
    }
}