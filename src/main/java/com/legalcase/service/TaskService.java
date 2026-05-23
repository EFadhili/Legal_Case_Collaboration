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
        log.info("Task created with ID: {}", saved.getId());

        return findById(saved.getId());
    }

    // ============================================
    // BASIC FIND METHODS
    // ============================================

    public Task findById(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task", id));
    }

    public List<Task> getTasksByCase(Long caseId) {
        LegalCase legalCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Case", caseId));
        return taskRepository.findByLegalCase(legalCase);
    }

    public List<Task> getTasksByAssignedUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        return taskRepository.findByAssignedTo(user);
    }

    // ============================================
    // STATUS & TYPE & PRIORITY METHODS
    // ============================================

    public List<Task> getTasksByStatus(TaskStatus status) {
        return taskRepository.findByStatus(status);
    }

    public List<Task> getTasksByType(TaskType type) {
        return taskRepository.findByType(type);
    }

    public List<Task> getTasksByPriority(TaskPriority priority) {
        return taskRepository.findByPriority(priority);
    }

    public List<Task> getTasksByAssignedTo(Long assignedToUserId) {
        User assignedTo = userRepository.findById(assignedToUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", assignedToUserId));
        return taskRepository.findByAssignedTo(assignedTo);
    }

    // ============================================
    // CASE + FILTER METHODS
    // ============================================

    public List<Task> getTasksByCaseAndStatus(Long caseId, TaskStatus status) {
        LegalCase legalCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Case", caseId));
        return taskRepository.findByLegalCaseAndStatus(legalCase, status);
    }

    public List<Task> getTasksByCaseAndAssignedTo(Long caseId, Long assignedToUserId) {
        LegalCase legalCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Case", caseId));
        User assignedTo = userRepository.findById(assignedToUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", assignedToUserId));
        return taskRepository.findByLegalCaseAndAssignedTo(legalCase, assignedTo);
    }

    public List<Task> getTasksByCaseAndType(Long caseId, TaskType type) {
        LegalCase legalCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Case", caseId));
        return taskRepository.findByLegalCaseAndType(legalCase, type);
    }

    // ============================================
    // DUE DATE METHODS
    // ============================================

    public List<Task> getTasksByDueDate(LocalDate dueDate) {
        return taskRepository.findByDueDate(dueDate);
    }

    public List<Task> getTasksByDueDateBefore(LocalDate date) {
        return taskRepository.findByDueDateBefore(date);
    }

    public List<Task> getTasksByDueDateAfter(LocalDate date) {
        return taskRepository.findByDueDateAfter(date);
    }

    public List<Task> getTasksByDueDateBetween(LocalDate startDate, LocalDate endDate) {
        return taskRepository.findByDueDateBetween(startDate, endDate);
    }

    // ============================================
    // OVERDUE METHODS
    // ============================================

    public List<Task> getOverdueTasksByCase(Long caseId) {
        return taskRepository.findOverdueTasksByCaseId(caseId);
    }

    public List<Task> getAllOverdueTasks() {
        return taskRepository.findOverdueTasks();
    }

    // ============================================
    // UNBLOCKED METHODS
    // ============================================

    public List<Task> getUnblockedTasksByCase(Long caseId) {
        LegalCase legalCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Case", caseId));
        List<Task> tasks = taskRepository.findByLegalCase(legalCase);
        return tasks.stream()
                .filter(t -> !t.isBlockedByDependency())
                .toList();
    }

    // ============================================
    // COUNT METHODS
    // ============================================

    public long countTasksByCaseAndType(Long caseId, TaskType type) {
        LegalCase legalCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Case", caseId));
        return taskRepository.countByLegalCaseAndType(legalCase, type);
    }

    public long countTasksByCaseAndTypeAndStatus(Long caseId, TaskType type, TaskStatus status) {
        LegalCase legalCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Case", caseId));
        return taskRepository.countByLegalCaseAndTypeAndStatus(legalCase, type, status);
    }

    public long countTasksByCaseAndStatus(Long caseId, TaskStatus status) {
        LegalCase legalCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Case", caseId));
        return taskRepository.countByLegalCaseAndStatus(legalCase, status);
    }

    // ============================================
    // UPDATE METHODS
    // ============================================

    @Transactional
    public Task updateStatus(Long taskId, TaskStatus newStatus, Long userId) {
        log.info("User {} updating task {} status to {}", userId, taskId, newStatus);

        Task task = findById(taskId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        LegalCase legalCase = task.getLegalCase();

        if (legalCase.isLocked()) {
            throw new InvalidStatusTransitionException("Cannot update tasks in a locked case");
        }

        boolean isAdmin = user.isAdmin();
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
            if (!isAssignedUser && !isCaseLawyer && !isAdmin) {
                throw new UnauthorizedException("Only assigned user, case lawyer, or admin can move task to REVIEW");
            }
        } else if (newStatus == TaskStatus.COMPLETED) {
            if (!isCaseLawyer && !isAdmin) {
                throw new UnauthorizedException("Only case lawyers or admins can approve and complete tasks");
            }
            task.setApprovedBy(user);
            task.setApprovedAt(LocalDateTime.now());
            task.setProgress(100);
        }

        task.setStatus(newStatus);

        if (newStatus == TaskStatus.IN_PROGRESS && task.getProgress() == 0) {
            task.setProgress(10);
        }

        taskRepository.save(task);
        log.info("Task {} status updated to {}", taskId, newStatus);

        return findById(taskId);
    }

    @Transactional
    public Task updateProgress(Long taskId, Integer progress, Long userId) {
        log.info("User {} updating task {} progress to {}%", userId, taskId, progress);

        Task task = findById(taskId);
        LegalCase legalCase = task.getLegalCase();

        if (legalCase.isLocked()) {
            throw new InvalidStatusTransitionException("Cannot update tasks in a locked case");
        }

        boolean isAssignedUser = task.getAssignedTo() != null && task.getAssignedTo().getId().equals(userId);
        boolean isAdmin = userRepository.findById(userId).map(User::isAdmin).orElse(false);

        if (!isAssignedUser && !isAdmin) {
            throw new UnauthorizedException("Only assigned user or admin can update task progress");
        }

        task.setProgress(progress);

        if (progress == 100 && task.getStatus() != TaskStatus.COMPLETED) {
            task.setStatus(TaskStatus.REVIEW);
            log.info("Task {} auto-moved to REVIEW due to 100% progress", taskId);
        } else if (progress > 0 && progress < 100 && task.getStatus() == TaskStatus.TODO) {
            task.setStatus(TaskStatus.IN_PROGRESS);
            log.info("Task {} auto-moved to IN_PROGRESS", taskId);
        }

        taskRepository.save(task);

        return findById(taskId);
    }

    @Transactional
    public Task assignTask(Long taskId, Long assignedToUserId, Long assignedByUserId) {
        log.info("User {} assigning task {} to user {}", assignedByUserId, taskId, assignedToUserId);

        Task task = findById(taskId);
        LegalCase legalCase = task.getLegalCase();

        if (legalCase.isLocked()) {
            throw new InvalidStatusTransitionException("Cannot assign tasks in a locked case");
        }

        User assignedTo = userRepository.findById(assignedToUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", assignedToUserId));

        if (!caseMemberRepository.existsByLegalCaseAndUser(legalCase, assignedTo)) {
            throw new AccessDeniedException("Assigned user must be a case member");
        }

        task.setAssignedTo(assignedTo);
        taskRepository.save(task);

        notificationService.notifyTaskAssigned(taskId, assignedToUserId, assignedByUserId);
        log.info("Task {} assigned to user {}", taskId, assignedToUserId);

        return findById(taskId);
    }

    @Transactional
    public void setProgressTo100OnCompletion(Long taskId) {
        taskRepository.setProgressTo100OnCompletion(taskId);
    }
}