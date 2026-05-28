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

    @Value("${document.processing.max-retries:3}")
    private int maxRetries;

    /**
     * Asynchronously extracts text from a document.
     * Runs in a separate thread with its own transaction.
     *
     * @param document The document to process
     * @return CompletableFuture representing the async operation
     */
    @Async("documentTaskExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CompletableFuture<Void> extractTextAsync(Document document) {
        log.info("Starting async text extraction for document: {}", document.getDocumentNumber());

        // Re-fetch document to ensure it's managed by the current persistence context
        Document managedDocument = documentRepository.findById(document.getId())
                .orElse(null);

        if (managedDocument == null) {
            log.error("Document {} not found, aborting extraction", document.getDocumentNumber());
            return CompletableFuture.completedFuture(null);
        }

        // Check if document is still active before processing
        if (managedDocument.isDeleted() || managedDocument.getStatus() != DocumentStatus.ACTIVE) {
            log.warn("Document {} is no longer active (deleted: {}, status: {}), skipping extraction",
                    managedDocument.getDocumentNumber(), managedDocument.isDeleted(), managedDocument.getStatus());
            return CompletableFuture.completedFuture(null);
        }

        // Check if already processed
        if (managedDocument.getTextExtractionStatus() == TextExtractionStatus.COMPLETED) {
            log.info("Document {} already has extracted text, skipping", managedDocument.getDocumentNumber());
            return CompletableFuture.completedFuture(null);
        }

        int retryCount = 0;
        Exception lastException = null;

        while (retryCount < maxRetries) {
            try {
                // Update status to PROCESSING
                updateExtractionStatus(managedDocument.getId(), TextExtractionStatus.PROCESSING, 0);
                log.info("Document {} processing started (attempt {}/{})",
                        managedDocument.getDocumentNumber(), retryCount + 1, maxRetries);

                // Extract text from the document
                String extractedText = extractTextFromDocument(managedDocument);

                // Mark as complete
                markExtractionComplete(managedDocument.getId(), extractedText);

                log.info("Text extraction completed for document: {} ({} characters extracted)",
                        managedDocument.getDocumentNumber(), extractedText.length());
                return CompletableFuture.completedFuture(null);

            } catch (Exception e) {
                lastException = e;
                retryCount++;
                log.error("Extraction failed for document {} (attempt {}/{}): {}",
                        managedDocument.getDocumentNumber(), retryCount, maxRetries, e.getMessage());

                if (retryCount >= maxRetries) {
                    markExtractionFailed(managedDocument.getId(),
                            "Failed after " + maxRetries + " attempts: " + e.getMessage());
                    log.error("Document {} extraction failed permanently after {} attempts",
                            managedDocument.getDocumentNumber(), maxRetries);
                } else {
                    // Exponential backoff before retry
                    long delay = 2000L * retryCount; // 2s, 4s, 6s
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

    /**
     * Updates the extraction status of a document.
     *
     * @param documentId The document ID
     * @param status The new status
     * @param progress The progress percentage (0-100)
     */
    private void updateExtractionStatus(Long documentId, TextExtractionStatus status, int progress) {
        try {
            documentRepository.updateExtractionStatus(documentId, status, progress, LocalDateTime.now());
            log.debug("Document {} status updated to: {} ({}%)", documentId, status, progress);
        } catch (Exception e) {
            log.error("Failed to update extraction status for document {}: {}", documentId, e.getMessage());
            throw new FileProcessingException("Failed to update extraction status: " + e.getMessage(), e);
        }
    }

    /**
     * Marks the document extraction as complete and saves the extracted text.
     *
     * @param documentId The document ID
     * @param extractedText The extracted text content
     */
    private void markExtractionComplete(Long documentId, String extractedText) {
        try {
            documentRepository.markExtractionComplete(documentId, extractedText, LocalDateTime.now());
            log.debug("Document {} marked as complete", documentId);
        } catch (Exception e) {
            log.error("Failed to mark extraction complete for document {}: {}", documentId, e.getMessage());
            throw new FileProcessingException("Failed to mark extraction complete: " + e.getMessage(), e);
        }
    }

    /**
     * Marks the document extraction as failed with an error message.
     *
     * @param documentId The document ID
     * @param error The error message
     */
    private void markExtractionFailed(Long documentId, String error) {
        try {
            documentRepository.markExtractionFailed(documentId, error);
            log.debug("Document {} marked as failed", documentId);
        } catch (Exception e) {
            log.error("Failed to mark extraction failed for document {}: {}", documentId, e.getMessage());
        }
    }

    /**
     * Extracts text from the document stored in S3.
     * Updates progress during extraction.
     *
     * @param document The document entity
     * @return The extracted text
     * @throws Exception If extraction fails
     */
    private String extractTextFromDocument(Document document) throws Exception {
        log.debug("Starting text extraction from S3 for document: {}", document.getDocumentNumber());

        // Update progress: 25%
        updateExtractionStatus(document.getId(), TextExtractionStatus.PROCESSING, 25);
        log.debug("Document {} extraction progress: 25%", document.getDocumentNumber());

        // Download and extract text
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

        // Validate extracted text
        if (extractedText == null) {
            extractedText = "";
            log.warn("No text extracted from document: {}", document.getDocumentNumber());
        }

        // Update progress: 75%
        updateExtractionStatus(document.getId(), TextExtractionStatus.PROCESSING, 75);
        log.debug("Document {} extraction progress: 75%, extracted {} characters",
                document.getDocumentNumber(), extractedText.length());

        return extractedText;
    }

    /**
     * Synchronously extracts text from a document (for immediate processing needs).
     *
     * @param document The document to process
     * @return The extracted text
     * @throws Exception If extraction fails
     */
    @Transactional
    public String extractTextSync(Document document) throws Exception {
        log.info("Starting sync text extraction for document: {}", document.getDocumentNumber());

        String extractedText = extractTextFromDocument(document);
        markExtractionComplete(document.getId(), extractedText);

        log.info("Sync text extraction completed for document: {}", document.getDocumentNumber());
        return extractedText;
    }

    /**
     * Cleans up stuck processing documents (can be called by a scheduler).
     * Finds documents that have been processing for too long and marks them as failed.
     */
    @Transactional
    public void cleanStuckProcessingDocuments() {
        int timeoutSeconds = 300; // 5 minutes
        LocalDateTime timeout = LocalDateTime.now().minusSeconds(timeoutSeconds);

        var stuckDocuments = documentRepository.findStuckProcessingDocuments(timeout);

        if (!stuckDocuments.isEmpty()) {
            log.warn("Found {} stuck processing documents", stuckDocuments.size());
            for (Document document : stuckDocuments) {
                log.warn("Marking stuck document {} as failed (processing since {})",
                        document.getDocumentNumber(), document.getProcessingStartedAt());
                markExtractionFailed(document.getId(),
                        "Processing timed out after " + timeoutSeconds + " seconds");
            }
        } else {
            log.debug("No stuck processing documents found");
        }
    }

    /**
     * Retries failed extraction for a document.
     *
     * @param documentId The document ID
     * @return true if retry was triggered, false otherwise
     */
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

        // Reset retry count and status
        documentRepository.updateExtractionStatus(documentId, TextExtractionStatus.PENDING, 0, LocalDateTime.now());

        // Trigger async extraction
        extractTextAsync(document);

        log.info("Retry triggered for document: {}", document.getDocumentNumber());
        return CompletableFuture.completedFuture(true);
    }
}