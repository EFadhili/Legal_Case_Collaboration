package com.legalcase.service;

import com.legalcase.entity.LegalCase;
import com.legalcase.entity.Task;
import com.legalcase.entity.User;
import com.legalcase.enums.TaskPriority;
import com.legalcase.enums.TaskStatus;
import com.legalcase.enums.TaskType;
import com.legalcase.repository.CaseMemberRepository;
import com.legalcase.repository.CaseRepository;
import com.legalcase.repository.TaskRepository;
import com.legalcase.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional
    public Task createTask(String title, String description, TaskType type,
                           TaskPriority priority, LocalDateTime dueDate,
                           Long caseId, Long createdById, Long assignedToUserId,
                           Long dependsOnTaskId) {

        log.info("Creating task: {} in case: {}", title, caseId);

        LegalCase legalCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new RuntimeException("Case not found with ID: " + caseId));

        if (legalCase.isLocked()) {
            throw new RuntimeException("Cannot create tasks in a locked case");
        }

        User createdBy = userRepository.findById(createdById)
                .orElseThrow(() -> new RuntimeException("User not found: " + createdById));

        if (!caseMemberRepository.existsByLegalCaseAndUser(legalCase, createdBy)) {
            throw new RuntimeException("Only case members can create tasks");
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
                    .orElseThrow(() -> new RuntimeException("User not found: " + assignedToUserId));

            if (!caseMemberRepository.existsByLegalCaseAndUser(legalCase, assignedTo)) {
                throw new RuntimeException("Assigned user must be a case member");
            }
            task.setAssignedTo(assignedTo);
        }

        if (dependsOnTaskId != null) {
            Task dependsOn = taskRepository.findById(dependsOnTaskId)
                    .orElseThrow(() -> new RuntimeException("Dependency task not found: " + dependsOnTaskId));
            task.setDependsOn(dependsOn);
        }

        Task saved = taskRepository.save(task);
        log.info("Task created with ID: {}", saved.getId());

        return findWithAllAssociations(saved.getId());
    }

    public Task findById(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Task not found with ID: " + id));
    }

    public Task findWithAllAssociations(Long taskId) {
        return taskRepository.findWithAllAssociations(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found with ID: " + taskId));
    }

    public List<Task> getTasksByCase(Long caseId) {
        LegalCase legalCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new RuntimeException("Case not found with ID: " + caseId));
        return taskRepository.findByLegalCaseWithDetails(legalCase);
    }

    public List<Task> getTasksByAssignedUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + userId));
        return taskRepository.findByAssignedToWithDetails(user);
    }

    @Transactional
    public Task updateStatus(Long taskId, TaskStatus newStatus, Long userId) {
        log.info("User {} updating task {} status to {}", userId, taskId, newStatus);

        Task task = findById(taskId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + userId));
        LegalCase legalCase = task.getLegalCase();

        if (legalCase.isLocked()) {
            throw new RuntimeException("Cannot update tasks in a locked case");
        }

        boolean isAdmin = user.isAdmin();
        boolean isAssignedUser = task.getAssignedTo() != null && task.getAssignedTo().getId().equals(userId);
        boolean isCaseLawyer = caseMemberRepository.findByLegalCaseAndUser(legalCase, user)
                .map(member -> member.getRole() == com.legalcase.enums.CaseMemberRole.LAWYER)
                .orElse(false);

        if (!task.canTransitionTo(newStatus)) {
            throw new RuntimeException(String.format(
                    "Invalid status transition from %s to %s", task.getStatus(), newStatus));
        }

        if (newStatus == TaskStatus.IN_PROGRESS && task.isBlockedByDependency()) {
            String dependencyTitle = task.getDependsOn() != null ? task.getDependsOn().getTitle() : "Unknown";
            throw new RuntimeException("Cannot start task: Dependency task '" + dependencyTitle + "' is not completed");
        }

        if (newStatus == TaskStatus.REVIEW) {
            if (!isAssignedUser && !isCaseLawyer && !isAdmin) {
                throw new RuntimeException("Only assigned user, case lawyer, or admin can move task to REVIEW");
            }
        } else if (newStatus == TaskStatus.COMPLETED) {
            if (!isCaseLawyer && !isAdmin) {
                throw new RuntimeException("Only case lawyers or admins can approve and complete tasks");
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

        return findWithAllAssociations(taskId);
    }

    @Transactional
    public Task updateProgress(Long taskId, Integer progress, Long userId) {
        log.info("User {} updating task {} progress to {}%", userId, taskId, progress);

        Task task = findById(taskId);
        LegalCase legalCase = task.getLegalCase();

        if (legalCase.isLocked()) {
            throw new RuntimeException("Cannot update tasks in a locked case");
        }

        boolean isAssignedUser = task.getAssignedTo() != null && task.getAssignedTo().getId().equals(userId);
        boolean isAdmin = userRepository.findById(userId).map(User::isAdmin).orElse(false);

        if (!isAssignedUser && !isAdmin) {
            throw new RuntimeException("Only assigned user or admin can update task progress");
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

        return findWithAllAssociations(taskId);
    }

    @Transactional
    public Task assignTask(Long taskId, Long assignedToUserId, Long assignedByUserId) {
        log.info("User {} assigning task {} to user {}", assignedByUserId, taskId, assignedToUserId);

        Task task = findById(taskId);
        LegalCase legalCase = task.getLegalCase();

        if (legalCase.isLocked()) {
            throw new RuntimeException("Cannot assign tasks in a locked case");
        }

        User assignedTo = userRepository.findById(assignedToUserId)
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + assignedToUserId));

        if (!caseMemberRepository.existsByLegalCaseAndUser(legalCase, assignedTo)) {
            throw new RuntimeException("Assigned user must be a case member");
        }

        task.setAssignedTo(assignedTo);
        taskRepository.save(task);
        log.info("Task {} assigned to user {}", taskId, assignedToUserId);

        return findWithAllAssociations(taskId);
    }

    public List<Task> getOverdueTasksByCase(Long caseId) {
        return taskRepository.findOverdueTasksByCaseId(caseId);
    }

    public List<Task> getUnblockedTasksByCase(Long caseId) {
        LegalCase legalCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new RuntimeException("Case not found with ID: " + caseId));
        List<Task> tasks = taskRepository.findByLegalCaseWithDetails(legalCase);
        return tasks.stream()
                .filter(t -> !t.isBlockedByDependency())
                .toList();
    }
}