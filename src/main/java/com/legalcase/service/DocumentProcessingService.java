package com.legalcase.service;

import com.legalcase.entity.Document;
import com.legalcase.enums.TextExtractionStatus;
import com.legalcase.exception.FileProcessingException;
import com.legalcase.exception.ResourceNotFoundException;
import com.legalcase.repository.DocumentRepository;
import com.legalcase.util.FileUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentProcessingService {

    private final DocumentRepository documentRepository;
    private final FileUtils fileUtils;

    @Value("${document.processing.max-time-seconds:300}")
    private int maxProcessingSeconds;

    @Value("${document.processing.max-retries:3}")
    private int maxRetries;

    @Async("documentTaskExecutor")
    @Transactional
    public CompletableFuture<Void> extractTextAsync(Document document) {
        log.info("Starting async text extraction for document: {}", document.getId());

        int retryCount = 0;

        while (retryCount < maxRetries) {
            try {
                documentRepository.updateExtractionStatus(
                        document.getId(),
                        TextExtractionStatus.PROCESSING,
                        0,
                        LocalDateTime.now()
                );

                processDocument(document);

                log.info("Text extraction completed for document: {}", document.getId());
                return CompletableFuture.completedFuture(null);

            } catch (Exception e) {
                retryCount++;
                log.error("Extraction failed for document {} (attempt {}/{}): {}",
                        document.getId(), retryCount, maxRetries, e.getMessage());

                if (retryCount >= maxRetries) {
                    documentRepository.markExtractionFailed(
                            document.getId(),
                            e.getMessage()
                    );
                }
            }
        }

        return CompletableFuture.completedFuture(null);
    }

    @Transactional
    protected void processDocument(Document document) {
        try {
            documentRepository.updateExtractionStatus(
                    document.getId(),
                    TextExtractionStatus.PROCESSING,
                    25,
                    document.getProcessingStartedAt()
            );

            String extractedText = extractTextFromS3(document);

            documentRepository.updateExtractionStatus(
                    document.getId(),
                    TextExtractionStatus.PROCESSING,
                    75,
                    document.getProcessingStartedAt()
            );

            documentRepository.markExtractionComplete(
                    document.getId(),
                    extractedText,
                    LocalDateTime.now()
            );

            documentRepository.updateExtractionStatus(
                    document.getId(),
                    TextExtractionStatus.PROCESSING,
                    100,
                    document.getProcessingStartedAt()
            );

        } catch (Exception e) {
            throw new FileProcessingException("Failed to process document: " + e.getMessage(), e);
        }
    }

    private String extractTextFromS3(Document document) throws Exception {
        try (InputStream inputStream = fileUtils.downloadFromS3(document.getStoragePath())) {
            return fileUtils.extractTextFromFile(inputStream, document.getFileExtension());
        } catch (Exception e) {
            throw new FileProcessingException("Failed to extract text from document: " + e.getMessage(), e);
        }
    }
}