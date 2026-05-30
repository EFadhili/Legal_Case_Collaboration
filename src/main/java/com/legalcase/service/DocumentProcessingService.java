package com.legalcase.service;

import com.legalcase.entity.Document;
import com.legalcase.enums.DocumentStatus;
import com.legalcase.enums.TextExtractionStatus;
import com.legalcase.exception.FileProcessingException;
import com.legalcase.repository.DocumentRepository;
import com.legalcase.util.FileUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentProcessingService {

    private final DocumentRepository documentRepository;
    private final FileUtils fileUtils;
    private final NotificationService notificationService;

    @Value("${document.processing.max-retries:3}")
    private int maxRetries;

    @Async("documentTaskExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CompletableFuture<Void> extractTextAsync(Document document) {
        log.info("Starting async text extraction for document: {}", document.getDocumentNumber());

        Document managedDocument = documentRepository.findById(document.getId())
                .orElse(null);

        if (managedDocument == null) {
            log.error("Document {} not found, aborting extraction", document.getDocumentNumber());
            return CompletableFuture.completedFuture(null);
        }

        if (managedDocument.isDeleted() || managedDocument.getStatus() != DocumentStatus.ACTIVE) {
            log.warn("Document {} is no longer active (deleted: {}, status: {}), skipping extraction",
                    managedDocument.getDocumentNumber(), managedDocument.isDeleted(), managedDocument.getStatus());
            return CompletableFuture.completedFuture(null);
        }

        if (managedDocument.getTextExtractionStatus() == TextExtractionStatus.COMPLETED) {
            log.info("Document {} already has extracted text, skipping", managedDocument.getDocumentNumber());
            return CompletableFuture.completedFuture(null);
        }

        int retryCount = 0;

        while (retryCount < maxRetries) {
            try {
                updateExtractionStatus(managedDocument.getId(), TextExtractionStatus.PROCESSING, 0);
                log.info("Document {} processing started (attempt {}/{})",
                        managedDocument.getDocumentNumber(), retryCount + 1, maxRetries);

                String extractedText = extractTextFromDocument(managedDocument);
                markExtractionComplete(managedDocument.getId(), extractedText);

                // Send success notification
                notificationService.notifyDocumentProcessed(managedDocument.getId(),
                        managedDocument.getUploadedBy().getId(), true, null);

                log.info("Text extraction completed for document: {} ({} characters extracted)",
                        managedDocument.getDocumentNumber(), extractedText.length());
                return CompletableFuture.completedFuture(null);

            } catch (Exception e) {
                retryCount++;
                log.error("Extraction failed for document {} (attempt {}/{}): {}",
                        managedDocument.getDocumentNumber(), retryCount, maxRetries, e.getMessage());

                if (retryCount >= maxRetries) {
                    markExtractionFailed(managedDocument.getId(),
                            "Failed after " + maxRetries + " attempts: " + e.getMessage());

                    // Send failure notification
                    notificationService.notifyDocumentProcessed(managedDocument.getId(),
                            managedDocument.getUploadedBy().getId(), false, e.getMessage());

                    log.error("Document {} extraction failed permanently after {} attempts",
                            managedDocument.getDocumentNumber(), maxRetries);
                } else {
                    long delay = 2000L * retryCount;
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.warn("Retry sleep interrupted for document {}", managedDocument.getDocumentNumber());
                        break;
                    }
                }
            }
        }

        return CompletableFuture.completedFuture(null);
    }

    private void updateExtractionStatus(Long documentId, TextExtractionStatus status, int progress) {
        try {
            documentRepository.updateExtractionStatus(documentId, status, progress, LocalDateTime.now());
            log.debug("Document {} status updated to: {} ({}%)", documentId, status, progress);
        } catch (Exception e) {
            log.error("Failed to update extraction status for document {}: {}", documentId, e.getMessage());
            throw new FileProcessingException("Failed to update extraction status: " + e.getMessage(), e);
        }
    }

    private void markExtractionComplete(Long documentId, String extractedText) {
        try {
            documentRepository.markExtractionComplete(documentId, extractedText, LocalDateTime.now());
            log.debug("Document {} marked as complete", documentId);
        } catch (Exception e) {
            log.error("Failed to mark extraction complete for document {}: {}", documentId, e.getMessage());
            throw new FileProcessingException("Failed to mark extraction complete: " + e.getMessage(), e);
        }
    }

    private void markExtractionFailed(Long documentId, String error) {
        try {
            documentRepository.markExtractionFailed(documentId, error);
            log.debug("Document {} marked as failed", documentId);
        } catch (Exception e) {
            log.error("Failed to mark extraction failed for document {}: {}", documentId, e.getMessage());
        }
    }

    private String extractTextFromDocument(Document document) throws Exception {
        log.debug("Starting text extraction from S3 for document: {}", document.getDocumentNumber());

        updateExtractionStatus(document.getId(), TextExtractionStatus.PROCESSING, 25);
        log.debug("Document {} extraction progress: 25%", document.getDocumentNumber());

        String extractedText;
        try (InputStream inputStream = fileUtils.downloadFromS3(document.getStoragePath())) {
            extractedText = fileUtils.extractTextFromFile(inputStream, document.getFileExtension());
            log.debug("Successfully downloaded and extracted text from S3 for document: {}",
                    document.getDocumentNumber());
        } catch (Exception e) {
            log.error("Failed to download or extract text from S3 for document {}: {}",
                    document.getDocumentNumber(), e.getMessage());
            throw new FileProcessingException("Failed to extract text from S3: " + e.getMessage(), e);
        }

        if (extractedText == null) {
            extractedText = "";
            log.warn("No text extracted from document: {}", document.getDocumentNumber());
        }

        updateExtractionStatus(document.getId(), TextExtractionStatus.PROCESSING, 75);
        log.debug("Document {} extraction progress: 75%, extracted {} characters",
                document.getDocumentNumber(), extractedText.length());

        return extractedText;
    }

    @Transactional
    public String extractTextSync(Document document) throws Exception {
        log.info("Starting sync text extraction for document: {}", document.getDocumentNumber());

        String extractedText = extractTextFromDocument(document);
        markExtractionComplete(document.getId(), extractedText);

        notificationService.notifyDocumentProcessed(document.getId(),
                document.getUploadedBy().getId(), true, null);

        log.info("Sync text extraction completed for document: {}", document.getDocumentNumber());
        return extractedText;
    }

    @Transactional
    public void cleanStuckProcessingDocuments() {
        int timeoutSeconds = 300;
        LocalDateTime timeout = LocalDateTime.now().minusSeconds(timeoutSeconds);

        var stuckDocuments = documentRepository.findStuckProcessingDocuments(timeout);

        if (!stuckDocuments.isEmpty()) {
            log.warn("Found {} stuck processing documents", stuckDocuments.size());
            for (Document document : stuckDocuments) {
                log.warn("Marking stuck document {} as failed (processing since {})",
                        document.getDocumentNumber(), document.getProcessingStartedAt());
                markExtractionFailed(document.getId(),
                        "Processing timed out after " + timeoutSeconds + " seconds");

                notificationService.notifyDocumentProcessed(document.getId(),
                        document.getUploadedBy().getId(), false, "Processing timed out");
            }
        } else {
            log.debug("No stuck processing documents found");
        }
    }

    @Async("documentTaskExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CompletableFuture<Boolean> retryFailedExtraction(Long documentId) {
        log.info("Retrying failed extraction for document ID: {}", documentId);

        Document document = documentRepository.findById(documentId).orElse(null);
        if (document == null) {
            log.error("Document {} not found for retry", documentId);
            return CompletableFuture.completedFuture(false);
        }

        if (document.getTextExtractionStatus() != TextExtractionStatus.FAILED) {
            log.warn("Document {} is not in FAILED state (current state: {}), cannot retry",
                    document.getDocumentNumber(), document.getTextExtractionStatus());
            return CompletableFuture.completedFuture(false);
        }

        documentRepository.updateExtractionStatus(documentId, TextExtractionStatus.PENDING, 0, LocalDateTime.now());
        extractTextAsync(document);

        log.info("Retry triggered for document: {}", document.getDocumentNumber());
        return CompletableFuture.completedFuture(true);
    }
}