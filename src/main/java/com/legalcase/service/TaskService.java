package com.legalcase.service;

import com.legalcase.entity.LegalCase;
import com.legalcase.entity.Task;
import com.legalcase.entity.User;
import com.legalcase.enums.TaskPriority;
import com.legalcase.enums.TaskStatus;
import com.legalcase.enums.TaskType;
import com.legalcase.exception.*;
import com.legalcase.repository.CaseMemberRepository;
import com.legalcase.repository.CaseRepository;
import com.legalcase.repository.TaskRepository;
import com.legalcase.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class TaskService {

    private final TaskRepository taskRepository;
    private final CaseRepository caseRepository;
    private final UserRepository userRepository;
    private final CaseMemberRepository caseMemberRepository;
    private final NotificationService notificationService;

    // ============================================
    // HELPER METHODS
    // ============================================

    private Task findTask(String identifier) {
        if (identifier == null || identifier.trim().isEmpty()) {
            throw new ValidationException("identifier", "Task identifier is required");
        }
        try {
            Long id = Long.parseLong(identifier);
            return findById(id);
        } catch (NumberFormatException e) {
            return findByTaskNumber(identifier);
        }
    }

    private Task findById(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task", id));
    }

    private Task findByTaskNumber(String taskNumber) {
        return taskRepository.findByTaskNumberWithDetails(taskNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Task", "taskNumber", taskNumber));
    }

    private User findUserByIdentifier(String identifier) {
        return userRepository.findByUsername(identifier)
                .or(() -> userRepository.findByEmail(identifier))
                .orElseThrow(() -> new ResourceNotFoundException("User", "username or email", identifier));
    }

    private void verifyTaskAccess(Task task, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        LegalCase legalCase = task.getLegalCase();
        if (!caseMemberRepository.existsByLegalCaseAndUser(legalCase, user)) {
            throw new AccessDeniedException("You do not have access to this task. Only case members can view tasks.");
        }
    }

    private void verifyTaskModification(Task task, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        LegalCase legalCase = task.getLegalCase();

        if (!caseMemberRepository.existsByLegalCaseAndUser(legalCase, user)) {
            throw new AccessDeniedException("You do not have access to this task. Only case members can modify tasks.");
        }

        boolean isAssignedUser = task.getAssignedTo() != null && task.getAssignedTo().getId().equals(userId);
        boolean isCaseLawyer = caseMemberRepository.findByLegalCaseAndUser(legalCase, user)
                .map(member -> member.getRole() == com.legalcase.enums.CaseMemberRole.LAWYER)
                .orElse(false);

        if (!isAssignedUser && !isCaseLawyer) {
            throw new AccessDeniedException("Only the assigned user or case lawyers can modify this task");
        }
    }

    private void verifyTaskAssignmentPermission(LegalCase legalCase, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        if (!caseMemberRepository.existsByLegalCaseAndUser(legalCase, user)) {
            throw new AccessDeniedException("You do not have access to this case. Only case members can assign tasks.");
        }

        boolean isCaseLawyer = caseMemberRepository.findByLegalCaseAndUser(legalCase, user)
                .map(member -> member.getRole() == com.legalcase.enums.CaseMemberRole.LAWYER)
                .orElse(false);

        if (!isCaseLawyer) {
            throw new AccessDeniedException("Only case lawyers can assign tasks");
        }
    }

    private void verifyCaseAccess(LegalCase legalCase, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        if (!caseMemberRepository.existsByLegalCaseAndUser(legalCase, user)) {
            throw new AccessDeniedException("You do not have access to this case. Only case members can view tasks.");
        }
    }

    // ============================================
    // CREATE
    // ============================================

    @Transactional
    public Task createTask(String title, String description, TaskType type,
                           TaskPriority priority, LocalDateTime dueDate,
                           Long caseId, Long createdById, Long assignedToUserId,
                           Long dependsOnTaskId) {

        log.info("Creating task: {} in case: {}", title, caseId);

        LegalCase legalCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Case", caseId));

        if (legalCase.isLocked()) {
            throw new InvalidStatusTransitionException("Cannot create tasks in a locked case");
        }

        User createdBy = userRepository.findById(createdById)
                .orElseThrow(() -> new ResourceNotFoundException("User", createdById));

        if (!caseMemberRepository.existsByLegalCaseAndUser(legalCase, createdBy)) {
            throw new AccessDeniedException("Only case members can create tasks");
        }

        Task task = new Task();
        task.setTitle(title);
        task.setDescription(description);
        task.setType(type);
        task.setPriority(priority != null ? priority : TaskPriority.MEDIUM);
        task.setStatus(TaskStatus.TODO);
        task.setProgress(0);
        task.setDueDate(dueDate != null ? dueDate.toLocalDate() : null);
        task.setLegalCase(legalCase);
        task.setCreatedBy(createdBy);

        long taskCount = taskRepository.countByLegalCase(legalCase) + 1;
        task.generateTaskNumber(caseId, taskCount);

        if (assignedToUserId != null) {
            User assignedTo = userRepository.findById(assignedToUserId)
                    .orElseThrow(() -> new ResourceNotFoundException("User", assignedToUserId));

            if (!caseMemberRepository.existsByLegalCaseAndUser(legalCase, assignedTo)) {
                throw new AccessDeniedException("Assigned user must be a case member");
            }
            task.setAssignedTo(assignedTo);
        }

        if (dependsOnTaskId != null) {
            Task dependsOn = taskRepository.findById(dependsOnTaskId)
                    .orElseThrow(() -> new ResourceNotFoundException("Dependency Task", dependsOnTaskId));
            task.setDependsOn(dependsOn);
        }

        Task saved = taskRepository.save(task);
        log.info("Task created with ID: {}, Task Number: {}", saved.getId(), saved.getTaskNumber());

        // Send notification if task is assigned
        if (assignedToUserId != null) {
            notificationService.notifyTaskAssigned(saved.getId(), assignedToUserId, createdById);
        }

        return saved;
    }

    // ============================================
    // READ (Single)
    // ============================================

    public Task getTask(String identifier, Long userId) {
        Task task = findTask(identifier);
        verifyTaskAccess(task, userId);
        return task;
    }

    // ============================================
    // READ (Lists)
    // ============================================

    public List<Task> getTasksByCase(Long caseId, Long userId) {
        LegalCase legalCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Case", caseId));

        verifyCaseAccess(legalCase, userId);
        return taskRepository.findByLegalCase(legalCase);
    }

    public List<Task> getTasksByAssignedUser(Long userId, Long requestingUserId) {
        if (!userId.equals(requestingUserId)) {
            throw new AccessDeniedException("You can only view your own assigned tasks");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        return taskRepository.findByAssignedTo(user);
    }

    public List<Task> getTasksByAssignedUserIdentifier(String identifier, Long requestingUserId) {
        User targetUser = findUserByIdentifier(identifier);

        if (!targetUser.getId().equals(requestingUserId)) {
            throw new AccessDeniedException("You can only view your own assigned tasks");
        }

        return taskRepository.findByAssignedTo(targetUser);
    }

    public List<Task> getTasksByCaseAndStatus(Long caseId, TaskStatus status, Long userId) {
        LegalCase legalCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Case", caseId));

        verifyCaseAccess(legalCase, userId);
        return taskRepository.findByLegalCaseAndStatus(legalCase, status);
    }

    public List<Task> getTasksByCaseAndType(Long caseId, TaskType type, Long userId) {
        LegalCase legalCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Case", caseId));

        verifyCaseAccess(legalCase, userId);
        return taskRepository.findByLegalCaseAndType(legalCase, type);
    }

    public List<Task> getTasksByCaseAndAssignedToIdentifier(Long caseId, String identifier, Long userId) {
        LegalCase legalCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Case", caseId));

        verifyCaseAccess(legalCase, userId);

        User assignedTo = findUserByIdentifier(identifier);
        return taskRepository.findByLegalCaseAndAssignedTo(legalCase, assignedTo);
    }

    public List<Task> getOverdueTasksByCase(Long caseId, Long userId) {
        LegalCase legalCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Case", caseId));

        verifyCaseAccess(legalCase, userId);
        return taskRepository.findOverdueTasksByCaseId(caseId);
    }

    public List<Task> getUnblockedTasksByCase(Long caseId, Long userId) {
        LegalCase legalCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Case", caseId));

        verifyCaseAccess(legalCase, userId);
        List<Task> tasks = taskRepository.findByLegalCase(legalCase);
        return tasks.stream()
                .filter(t -> !t.isBlockedByDependency())
                .toList();
    }

    // ============================================
    // SEARCH
    // ============================================

    public List<Task> searchTasksInCase(Long caseId, String searchTerm, Long userId) {
        if (searchTerm == null || searchTerm.trim().isEmpty()) {
            return List.of();
        }

        LegalCase legalCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Case", caseId));

        verifyCaseAccess(legalCase, userId);
        return taskRepository.searchTasksInCaseByTitleOrNumber(caseId, searchTerm.trim());
    }

    // ============================================
    // COUNTS
    // ============================================

    public long countTasksByCaseAndType(Long caseId, TaskType type, Long userId) {
        LegalCase legalCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Case", caseId));

        verifyCaseAccess(legalCase, userId);
        return taskRepository.countByLegalCaseAndType(legalCase, type);
    }

    public long countTasksByCaseAndTypeAndStatus(Long caseId, TaskType type, TaskStatus status, Long userId) {
        LegalCase legalCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Case", caseId));

        verifyCaseAccess(legalCase, userId);
        return taskRepository.countByLegalCaseAndTypeAndStatus(legalCase, type, status);
    }

    public long countTasksByCaseAndStatus(Long caseId, TaskStatus status, Long userId) {
        LegalCase legalCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Case", caseId));

        verifyCaseAccess(legalCase, userId);
        return taskRepository.countByLegalCaseAndStatus(legalCase, status);
    }

    // ============================================
    // UPDATES
    // ============================================

    @Transactional
    public Task updateStatus(String identifier, TaskStatus newStatus, Long userId) {
        log.info("User {} updating task {} status to {}", userId, identifier, newStatus);

        Task task = findTask(identifier);
        verifyTaskModification(task, userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        LegalCase legalCase = task.getLegalCase();

        if (legalCase.isLocked()) {
            throw new InvalidStatusTransitionException("Cannot update tasks in a locked case");
        }

        boolean isAssignedUser = task.getAssignedTo() != null && task.getAssignedTo().getId().equals(userId);
        boolean isCaseLawyer = caseMemberRepository.findByLegalCaseAndUser(legalCase, user)
                .map(member -> member.getRole() == com.legalcase.enums.CaseMemberRole.LAWYER)
                .orElse(false);

        if (!task.canTransitionTo(newStatus)) {
            throw new InvalidStatusTransitionException(String.format(
                    "Invalid status transition from %s to %s", task.getStatus(), newStatus));
        }

        if (newStatus == TaskStatus.IN_PROGRESS && task.isBlockedByDependency()) {
            String dependencyTitle = task.getDependsOn() != null ? task.getDependsOn().getTitle() : "Unknown";
            throw new InvalidStatusTransitionException("Cannot start task: Dependency task '" + dependencyTitle + "' is not completed");
        }

        if (newStatus == TaskStatus.REVIEW) {
            if (!isAssignedUser && !isCaseLawyer) {
                throw new UnauthorizedException("Only assigned user or case lawyer can move task to REVIEW");
            }
        } else if (newStatus == TaskStatus.COMPLETED) {
            if (!isCaseLawyer) {
                throw new UnauthorizedException("Only case lawyers can approve and complete tasks");
            }
            task.setApprovedBy(user);
            task.setApprovedAt(LocalDateTime.now());
            task.setProgress(100);

            // Notify task creator and case owner about completion
            notificationService.notifyTaskCompleted(task.getId(), userId);

            // Notify tasks that depend on this task
            List<Task> dependentTasks = taskRepository.findByDependsOnId(task.getId());
            for (Task dependentTask : dependentTasks) {
                if (dependentTask.getAssignedTo() != null && dependentTask.getStatus() == TaskStatus.TODO) {
                    notificationService.notifyTaskDependencyMet(dependentTask.getId(), dependentTask.getAssignedTo().getId());
                }
            }
        }

        task.setStatus(newStatus);

        if (newStatus == TaskStatus.IN_PROGRESS && task.getProgress() == 0) {
            task.setProgress(10);
        }

        taskRepository.save(task);
        log.info("Task {} status updated to {}", task.getTaskNumber(), newStatus);
        return task;
    }

    @Transactional
    public Task updateProgress(String identifier, Integer progress, Long userId) {
        log.info("User {} updating task {} progress to {}%", userId, identifier, progress);

        Task task = findTask(identifier);
        verifyTaskModification(task, userId);

        LegalCase legalCase = task.getLegalCase();

        if (legalCase.isLocked()) {
            throw new InvalidStatusTransitionException("Cannot update tasks in a locked case");
        }

        boolean isAssignedUser = task.getAssignedTo() != null && task.getAssignedTo().getId().equals(userId);

        if (!isAssignedUser) {
            throw new UnauthorizedException("Only the assigned user can update task progress");
        }

        task.setProgress(progress);

        if (progress == 100 && task.getStatus() != TaskStatus.COMPLETED) {
            task.setStatus(TaskStatus.REVIEW);
            log.info("Task {} auto-moved to REVIEW due to 100% progress", task.getTaskNumber());
        } else if (progress > 0 && progress < 100 && task.getStatus() == TaskStatus.TODO) {
            task.setStatus(TaskStatus.IN_PROGRESS);
            log.info("Task {} auto-moved to IN_PROGRESS", task.getTaskNumber());
        }

        taskRepository.save(task);
        return task;
    }

    @Transactional
    public Task assignTaskByIdentifier(String taskIdentifier, String userIdentifier, Long assignedByUserId) {
        log.info("User {} assigning task {} to user identified by: {}", assignedByUserId, taskIdentifier, userIdentifier);

        Task task = findTask(taskIdentifier);
        LegalCase legalCase = task.getLegalCase();

        verifyTaskAssignmentPermission(legalCase, assignedByUserId);

        if (legalCase.isLocked()) {
            throw new InvalidStatusTransitionException("Cannot assign tasks in a locked case");
        }

        User assignedTo = findUserByIdentifier(userIdentifier);

        if (!caseMemberRepository.existsByLegalCaseAndUser(legalCase, assignedTo)) {
            throw new AccessDeniedException("Assigned user must be a case member");
        }

        task.setAssignedTo(assignedTo);
        taskRepository.save(task);

        notificationService.notifyTaskAssigned(task.getId(), assignedTo.getId(), assignedByUserId);
        log.info("Task {} assigned to user {}", task.getTaskNumber(), assignedTo.getUsername());
        return task;
    }

    @Transactional
    public void setProgressTo100OnCompletion(String identifier, Long userId) {
        Task task = findTask(identifier);
        verifyTaskModification(task, userId);
        taskRepository.setProgressTo100OnCompletion(task.getId());
        log.info("Task {} set to 100% completion", task.getTaskNumber());
    }

    @Transactional
    public void deleteTask(String identifier, Long userId) {
        Task task = findTask(identifier);
        verifyTaskModification(task, userId);

        LegalCase legalCase = task.getLegalCase();

        if (legalCase.isLocked()) {
            throw new InvalidStatusTransitionException("Cannot delete tasks in a locked case");
        }

        taskRepository.delete(task);
        log.info("Task {} deleted", task.getTaskNumber());
    }

    // ============================================
    // DUE DATE METHODS
    // ============================================

    public List<Task> getTasksByDueDate(Long caseId, LocalDate dueDate, Long userId) {
        LegalCase legalCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Case", caseId));

        verifyCaseAccess(legalCase, userId);
        return taskRepository.findByDueDate(dueDate);
    }

    public List<Task> getTasksByDueDateBefore(Long caseId, LocalDate date, Long userId) {
        LegalCase legalCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Case", caseId));

        verifyCaseAccess(legalCase, userId);
        return taskRepository.findByDueDateBefore(date);
    }

    public List<Task> getTasksByDueDateAfter(Long caseId, LocalDate date, Long userId) {
        LegalCase legalCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Case", caseId));

        verifyCaseAccess(legalCase, userId);
        return taskRepository.findByDueDateAfter(date);
    }

    public List<Task> getTasksByDueDateBetween(Long caseId, LocalDate startDate, LocalDate endDate, Long userId) {
        LegalCase legalCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Case", caseId));

        verifyCaseAccess(legalCase, userId);
        return taskRepository.findByDueDateBetween(startDate, endDate);
    }

    // ============================================
    // SCHEDULER METHODS FOR NOTIFICATIONS
    // ============================================

    @Transactional
    public void checkDeadlineApproachingTasks() {
        LocalDate today = LocalDate.now();
        LocalDate threeDaysFromNow = today.plusDays(3);

        List<Task> tasksDueSoon = taskRepository.findByDueDateBetween(today, threeDaysFromNow);

        int notifiedCount = 0;
        for (Task task : tasksDueSoon) {
            if (task.getAssignedTo() != null && task.getStatus() != TaskStatus.COMPLETED) {
                int daysRemaining = (int) java.time.temporal.ChronoUnit.DAYS.between(today, task.getDueDate());
                // Only notify if days remaining is positive and task not completed
                if (daysRemaining > 0 && daysRemaining <= 3) {
                    notificationService.notifyTaskDeadlineApproaching(task.getId(), task.getAssignedTo().getId(), daysRemaining);
                    notifiedCount++;
                    log.debug("Notified about task deadline: {} (due in {} days)", task.getTaskNumber(), daysRemaining);
                }
            }
        }

        if (notifiedCount > 0) {
            log.info("Sent {} deadline approaching notifications", notifiedCount);
        }
    }

    @Transactional
    public void checkOverdueTasks() {
        LocalDate today = LocalDate.now();
        List<Task> overdueTasks = taskRepository.findByDueDateBefore(today);

        int notifiedCount = 0;
        for (Task task : overdueTasks) {
            if (task.getAssignedTo() != null && task.getStatus() != TaskStatus.COMPLETED) {
                notificationService.notifyTaskOverdue(task.getId(), task.getAssignedTo().getId());
                notifiedCount++;
                log.debug("Notified about overdue task: {}", task.getTaskNumber());
            }
        }

        if (notifiedCount > 0) {
            log.info("Sent {} overdue task notifications", notifiedCount);
        }
    }
}