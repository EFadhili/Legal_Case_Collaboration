package com.legalcase.scheduler;

import com.legalcase.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class AuditCleanupScheduler {

    private final AuditLogRepository auditLogRepository;

    @Value("${audit.retention.years:7}")
    private int retentionYears;

    /**
     * Run daily at 2 AM to delete audit logs older than retention period
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void cleanupOldAuditLogs() {
        LocalDateTime cutoffDate = LocalDateTime.now().minusYears(retentionYears);
        log.info("Starting audit log cleanup. Deleting logs older than: {}", cutoffDate);

        long deletedCount = auditLogRepository.deleteOlderThan(cutoffDate);

        log.info("Audit log cleanup completed. Deleted {} old audit logs.", deletedCount);
    }
}