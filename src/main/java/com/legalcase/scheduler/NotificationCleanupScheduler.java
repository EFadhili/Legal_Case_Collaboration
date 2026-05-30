package com.legalcase.scheduler;

import com.legalcase.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class NotificationCleanupScheduler {

    private final NotificationService notificationService;

    /**
     * Run daily at 4 AM to permanently delete notifications that are READ or ARCHIVED and older than 30 days.
     */
    @Scheduled(cron = "0 0 4 * * *")
    public void cleanupOldNotifications() {
        log.info("Starting notification cleanup job...");

        int deletedCount = notificationService.deleteOldReadAndArchivedNotifications();

        log.info("Notification cleanup completed. Permanently deleted {} old read/archived notifications.", deletedCount);
    }
}