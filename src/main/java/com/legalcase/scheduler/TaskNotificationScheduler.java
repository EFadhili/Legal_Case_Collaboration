package com.legalcase.scheduler;

import com.legalcase.service.TaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class TaskNotificationScheduler {

    private final TaskService taskService;

    /**
     * Run daily at 9 AM to check for tasks with approaching deadlines
     */
    @Scheduled(cron = "0 0 9 * * *")
    public void checkDeadlineApproachingTasks() {
        log.info("Starting task deadline approaching check...");
        taskService.checkDeadlineApproachingTasks();
        log.info("Task deadline check completed.");
    }

    /**
     * Run daily at 9 AM to check for overdue tasks
     */
    @Scheduled(cron = "0 0 9 * * *")
    public void checkOverdueTasks() {
        log.info("Starting task overdue check...");
        taskService.checkOverdueTasks();
        log.info("Task overdue check completed.");
    }
}