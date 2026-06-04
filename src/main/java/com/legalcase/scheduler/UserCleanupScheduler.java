package com.legalcase.scheduler;

import com.legalcase.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class UserCleanupScheduler {

    private final UserService userService;

    /**
     * Run daily at 3 AM to permanently delete users soft-deleted over 30 days ago
     */
    @Scheduled(cron = "0 0 3 * * *")
    public void cleanupSoftDeletedUsers() {
        log.info("Starting user cleanup job...");

        int deletedCount = userService.permanentlyDeleteSoftDeletedUsersOlderThan(30);

        log.info("User cleanup completed. Permanently deleted {} soft-deleted users.", deletedCount);
    }
}