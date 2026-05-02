package com.legalcase.repository;

import com.legalcase.entity.LegalCase;
import com.legalcase.entity.Task;
import com.legalcase.entity.User;
import com.legalcase.enums.TaskPriority;
import com.legalcase.enums.TaskStatus;
import com.legalcase.enums.TaskType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {

    // Basic queries
    List<Task> findByLegalCase(LegalCase legalCase);

    List<Task> findByAssignedTo(User user);

    List<Task> findByStatus(TaskStatus status);

    List<Task> findByType(TaskType type);

    List<Task> findByPriority(TaskPriority priority);

    List<Task> findByDueDateBefore(LocalDate date);

    List<Task> findByDueDateAfter(LocalDate date);

    // Composite queries
    List<Task> findByLegalCaseAndStatus(LegalCase legalCase, TaskStatus status);

    List<Task> findByLegalCaseAndAssignedTo(LegalCase legalCase, User assignedTo);

    List<Task> findByLegalCaseAndType(LegalCase legalCase, TaskType type);

    // Count queries
    long countByLegalCaseAndType(LegalCase legalCase, TaskType type);

    long countByLegalCaseAndTypeAndStatus(LegalCase legalCase, TaskType type, TaskStatus status);

    long countByLegalCaseAndStatus(LegalCase legalCase, TaskStatus status);

    // ===== JOIN FETCH methods to prevent LazyInitializationException =====

    @Query("SELECT DISTINCT t FROM Task t " +
            "LEFT JOIN FETCH t.createdBy " +
            "LEFT JOIN FETCH t.assignedTo " +
            "LEFT JOIN FETCH t.approvedBy " +
            "LEFT JOIN FETCH t.dependsOn " +
            "WHERE t.legalCase = :legalCase")
    List<Task> findByLegalCaseWithDetails(@Param("legalCase") LegalCase legalCase);

    @Query("SELECT DISTINCT t FROM Task t " +
            "LEFT JOIN FETCH t.createdBy " +
            "LEFT JOIN FETCH t.legalCase " +
            "LEFT JOIN FETCH t.dependsOn " +
            "WHERE t.assignedTo = :user")
    List<Task> findByAssignedToWithDetails(@Param("user") User user);

    @Query("SELECT DISTINCT t FROM Task t " +
            "LEFT JOIN FETCH t.createdBy " +
            "LEFT JOIN FETCH t.assignedTo " +
            "LEFT JOIN FETCH t.approvedBy " +
            "LEFT JOIN FETCH t.dependsOn " +
            "LEFT JOIN FETCH t.legalCase c " +
            "LEFT JOIN FETCH c.owner " +
            "WHERE t.id = :taskId")
    Optional<Task> findWithAllAssociations(@Param("taskId") Long taskId);

    // Overdue tasks
    @Query("SELECT t FROM Task t WHERE t.dueDate < CURRENT_DATE AND t.status != 'COMPLETED'")
    List<Task> findOverdueTasks();

    @Query("SELECT t FROM Task t WHERE t.legalCase.id = :caseId AND t.dueDate < CURRENT_DATE AND t.status != 'COMPLETED'")
    List<Task> findOverdueTasksByCaseId(@Param("caseId") Long caseId);

    @Modifying
    @Query("UPDATE Task t SET t.progress = 100 WHERE t.id = :taskId AND t.status = 'COMPLETED'")
    void setProgressTo100OnCompletion(@Param("taskId") Long taskId);
}