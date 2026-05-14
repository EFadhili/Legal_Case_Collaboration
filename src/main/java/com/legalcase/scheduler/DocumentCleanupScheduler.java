package com.legalcase.scheduler;

import com.legalcase.repository.DocumentRepository;
import com.legalcase.service.DocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class DocumentCleanupScheduler {

    private final DocumentRepository documentRepository;
    private final DocumentService documentService;

    /**
     * Run daily at 2 AM to permanently delete documents soft-deleted over 30 days ago.
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void cleanupOldDocuments() {
        log.info("Starting document cleanup job...");

        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);

        // Get documents soft-deleted more than 30 days ago
        var documents = documentRepository.findByIsDeletedTrueAndDeletedAtBefore(thirtyDaysAgo);

        int deletedCount = 0;
        for (var document : documents) {
            try {
                documentService.permanentlyDelete(document.getId());
                deletedCount++;
            } catch (Exception e) {
                log.error("Failed to permanently delete document {}: {}", document.getId(), e.getMessage());
            }
        }

        log.info("Document cleanup completed. Permanently deleted {} documents.", deletedCount);
    }
}