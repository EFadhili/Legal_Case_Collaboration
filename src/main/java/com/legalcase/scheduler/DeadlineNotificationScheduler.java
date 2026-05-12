package com.legalcase.scheduler;

import com.legalcase.entity.LegalCase;
import com.legalcase.entity.Task;
import com.legalcase.repository.CaseRepository;
import com.legalcase.repository.TaskRepository;
import com.legalcase.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class DeadlineNotificationScheduler {

    private final TaskRepository taskRepository;
    private final CaseRepository caseRepository;
    private final NotificationService notificationService;

    /**
     * Check for approaching task deadlines every hour.
     */
    @Scheduled(cron = "0 0 * * * *")  // Run every hour
    public void checkTaskDeadlines() {
        log.info("Checking task deadlines...");

        LocalDate today = LocalDate.now();

        // Check tasks due in 7 days
        LocalDate sevenDaysFromNow = today.plusDays(7);
        List<Task> tasksDueIn7Days = taskRepository.findByDueDate(sevenDaysFromNow);
        for (Task task : tasksDueIn7Days) {
            if (task.getAssignedTo() != null && task.getStatus() != com.legalcase.enums.TaskStatus.COMPLETED) {
                notificationService.notifyTaskDeadlineApproaching(task.getId(), task.getAssignedTo().getId(), 7);
            }
        }

        // Check tasks due in 3 days
        LocalDate threeDaysFromNow = today.plusDays(3);
        List<Task> tasksDueIn3Days = taskRepository.findByDueDate(threeDaysFromNow);
        for (Task task : tasksDueIn3Days) {
            if (task.getAssignedTo() != null && task.getStatus() != com.legalcase.enums.TaskStatus.COMPLETED) {
                notificationService.notifyTaskDeadlineApproaching(task.getId(), task.getAssignedTo().getId(), 3);
            }
        }

        // Check tasks due in 1 day
        LocalDate tomorrow = today.plusDays(1);
        List<Task> tasksDueTomorrow = taskRepository.findByDueDate(tomorrow);
        for (Task task : tasksDueTomorrow) {
            if (task.getAssignedTo() != null && task.getStatus() != com.legalcase.enums.TaskStatus.COMPLETED) {
                notificationService.notifyTaskDeadlineApproaching(task.getId(), task.getAssignedTo().getId(), 1);
            }
        }

        // Check overdue tasks
        List<Task> overdueTasks = taskRepository.findByDueDateBefore(today);
        for (Task task : overdueTasks) {
            if (task.getAssignedTo() != null && task.getStatus() != com.legalcase.enums.TaskStatus.COMPLETED) {
                notificationService.notifyTaskOverdue(task.getId(), task.getAssignedTo().getId());
            }
        }
    }

    /**
     * Check for approaching case deadlines every 6 hours.
     */
    @Scheduled(cron = "0 0 */6 * * *")  // Run every 6 hours
    public void checkCaseDeadlines() {
        log.info("Checking case deadlines...");

        LocalDate today = LocalDate.now();

        // Check cases due in 7 days
        LocalDate sevenDaysFromNow = today.plusDays(7);
        List<LegalCase> casesDueIn7Days = caseRepository.findByDueDate(sevenDaysFromNow);
        for (LegalCase legalCase : casesDueIn7Days) {
            if (legalCase.getStatus() != com.legalcase.enums.CaseStatus.CLOSED &&
                    legalCase.getStatus() != com.legalcase.enums.CaseStatus.ARCHIVED) {
                notificationService.notifyCaseDeadlineApproaching(legalCase.getId(), legalCase.getOwner().getId(), 7);
            }
        }

        // Check cases due in 3 days
        LocalDate threeDaysFromNow = today.plusDays(3);
        List<LegalCase> casesDueIn3Days = caseRepository.findByDueDate(threeDaysFromNow);
        for (LegalCase legalCase : casesDueIn3Days) {
            if (legalCase.getStatus() != com.legalcase.enums.CaseStatus.CLOSED &&
                    legalCase.getStatus() != com.legalcase.enums.CaseStatus.ARCHIVED) {
                notificationService.notifyCaseDeadlineApproaching(legalCase.getId(), legalCase.getOwner().getId(), 3);
            }
        }

        // Check cases due in 1 day
        LocalDate tomorrow = today.plusDays(1);
        List<LegalCase> casesDueTomorrow = caseRepository.findByDueDate(tomorrow);
        for (LegalCase legalCase : casesDueTomorrow) {
            if (legalCase.getStatus() != com.legalcase.enums.CaseStatus.CLOSED &&
                    legalCase.getStatus() != com.legalcase.enums.CaseStatus.ARCHIVED) {
                notificationService.notifyCaseDeadlineApproaching(legalCase.getId(), legalCase.getOwner().getId(), 1);
            }
        }
    }
}