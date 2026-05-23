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

    // ============================================
    // OVERRIDE DEFAULT METHODS WITH JOIN FETCH
    // ============================================

    @Override
    @Query("SELECT DISTINCT t FROM Task t " +
            "LEFT JOIN FETCH t.createdBy " +
            "LEFT JOIN FETCH t.assignedTo " +
            "LEFT JOIN FETCH t.approvedBy " +
            "LEFT JOIN FETCH t.dependsOn " +
            "LEFT JOIN FETCH t.legalCase c " +
            "LEFT JOIN FETCH c.owner " +
            "WHERE t.id = :id")
    Optional<Task> findById(@Param("id") Long id);

    // ============================================
    // EXISTING METHOD NAMES WITH JOIN FETCH
    // ============================================

    @Query("SELECT DISTINCT t FROM Task t " +
            "LEFT JOIN FETCH t.createdBy " +
            "LEFT JOIN FETCH t.assignedTo " +
            "LEFT JOIN FETCH t.approvedBy " +
            "LEFT JOIN FETCH t.dependsOn " +
            "LEFT JOIN FETCH t.legalCase c " +
            "LEFT JOIN FETCH c.owner " +
            "WHERE t.legalCase = :legalCase")
    List<Task> findByLegalCase(@Param("legalCase") LegalCase legalCase);

    @Query("SELECT DISTINCT t FROM Task t " +
            "LEFT JOIN FETCH t.createdBy " +
            "LEFT JOIN FETCH t.assignedTo " +
            "LEFT JOIN FETCH t.approvedBy " +
            "LEFT JOIN FETCH t.dependsOn " +
            "LEFT JOIN FETCH t.legalCase c " +
            "LEFT JOIN FETCH c.owner " +
            "WHERE t.assignedTo = :user")
    List<Task> findByAssignedTo(@Param("user") User user);

    @Query("SELECT DISTINCT t FROM Task t " +
            "LEFT JOIN FETCH t.createdBy " +
            "LEFT JOIN FETCH t.assignedTo " +
            "LEFT JOIN FETCH t.approvedBy " +
            "LEFT JOIN FETCH t.dependsOn " +
            "LEFT JOIN FETCH t.legalCase c " +
            "LEFT JOIN FETCH c.owner " +
            "WHERE t.status = :status")
    List<Task> findByStatus(@Param("status") TaskStatus status);

    @Query("SELECT DISTINCT t FROM Task t " +
            "LEFT JOIN FETCH t.createdBy " +
            "LEFT JOIN FETCH t.assignedTo " +
            "LEFT JOIN FETCH t.approvedBy " +
            "LEFT JOIN FETCH t.dependsOn " +
            "LEFT JOIN FETCH t.legalCase c " +
            "LEFT JOIN FETCH c.owner " +
            "WHERE t.type = :type")
    List<Task> findByType(@Param("type") TaskType type);

    @Query("SELECT DISTINCT t FROM Task t " +
            "LEFT JOIN FETCH t.createdBy " +
            "LEFT JOIN FETCH t.assignedTo " +
            "LEFT JOIN FETCH t.approvedBy " +
            "LEFT JOIN FETCH t.dependsOn " +
            "LEFT JOIN FETCH t.legalCase c " +
            "LEFT JOIN FETCH c.owner " +
            "WHERE t.priority = :priority")
    List<Task> findByPriority(@Param("priority") TaskPriority priority);

    @Query("SELECT DISTINCT t FROM Task t " +
            "LEFT JOIN FETCH t.createdBy " +
            "LEFT JOIN FETCH t.assignedTo " +
            "LEFT JOIN FETCH t.approvedBy " +
            "LEFT JOIN FETCH t.dependsOn " +
            "LEFT JOIN FETCH t.legalCase c " +
            "LEFT JOIN FETCH c.owner " +
            "WHERE t.dueDate = :dueDate")
    List<Task> findByDueDate(@Param("dueDate") LocalDate dueDate);

    @Query("SELECT DISTINCT t FROM Task t " +
            "LEFT JOIN FETCH t.createdBy " +
            "LEFT JOIN FETCH t.assignedTo " +
            "LEFT JOIN FETCH t.approvedBy " +
            "LEFT JOIN FETCH t.dependsOn " +
            "LEFT JOIN FETCH t.legalCase c " +
            "LEFT JOIN FETCH c.owner " +
            "WHERE t.dueDate < :date")
    List<Task> findByDueDateBefore(@Param("date") LocalDate date);

    @Query("SELECT DISTINCT t FROM Task t " +
            "LEFT JOIN FETCH t.createdBy " +
            "LEFT JOIN FETCH t.assignedTo " +
            "LEFT JOIN FETCH t.approvedBy " +
            "LEFT JOIN FETCH t.dependsOn " +
            "LEFT JOIN FETCH t.legalCase c " +
            "LEFT JOIN FETCH c.owner " +
            "WHERE t.dueDate > :date")
    List<Task> findByDueDateAfter(@Param("date") LocalDate date);

    @Query("SELECT DISTINCT t FROM Task t " +
            "LEFT JOIN FETCH t.createdBy " +
            "LEFT JOIN FETCH t.assignedTo " +
            "LEFT JOIN FETCH t.approvedBy " +
            "LEFT JOIN FETCH t.dependsOn " +
            "LEFT JOIN FETCH t.legalCase c " +
            "LEFT JOIN FETCH c.owner " +
            "WHERE t.dueDate BETWEEN :startDate AND :endDate")
    List<Task> findByDueDateBetween(@Param("startDate") LocalDate startDate,
                                    @Param("endDate") LocalDate endDate);

    @Query("SELECT DISTINCT t FROM Task t " +
            "LEFT JOIN FETCH t.createdBy " +
            "LEFT JOIN FETCH t.assignedTo " +
            "LEFT JOIN FETCH t.approvedBy " +
            "LEFT JOIN FETCH t.dependsOn " +
            "LEFT JOIN FETCH t.legalCase c " +
            "LEFT JOIN FETCH c.owner " +
            "WHERE t.legalCase = :legalCase AND t.status = :status")
    List<Task> findByLegalCaseAndStatus(@Param("legalCase") LegalCase legalCase,
                                        @Param("status") TaskStatus status);

    @Query("SELECT DISTINCT t FROM Task t " +
            "LEFT JOIN FETCH t.createdBy " +
            "LEFT JOIN FETCH t.assignedTo " +
            "LEFT JOIN FETCH t.approvedBy " +
            "LEFT JOIN FETCH t.dependsOn " +
            "LEFT JOIN FETCH t.legalCase c " +
            "LEFT JOIN FETCH c.owner " +
            "WHERE t.legalCase = :legalCase AND t.assignedTo = :assignedTo")
    List<Task> findByLegalCaseAndAssignedTo(@Param("legalCase") LegalCase legalCase,
                                            @Param("assignedTo") User assignedTo);

    @Query("SELECT DISTINCT t FROM Task t " +
            "LEFT JOIN FETCH t.createdBy " +
            "LEFT JOIN FETCH t.assignedTo " +
            "LEFT JOIN FETCH t.approvedBy " +
            "LEFT JOIN FETCH t.dependsOn " +
            "LEFT JOIN FETCH t.legalCase c " +
            "LEFT JOIN FETCH c.owner " +
            "WHERE t.legalCase = :legalCase AND t.type = :type")
    List<Task> findByLegalCaseAndType(@Param("legalCase") LegalCase legalCase,
                                      @Param("type") TaskType type);

    @Query("SELECT DISTINCT t FROM Task t " +
            "LEFT JOIN FETCH t.createdBy " +
            "LEFT JOIN FETCH t.assignedTo " +
            "LEFT JOIN FETCH t.approvedBy " +
            "LEFT JOIN FETCH t.dependsOn " +
            "LEFT JOIN FETCH t.legalCase c " +
            "LEFT JOIN FETCH c.owner " +
            "WHERE t.dueDate < CURRENT_DATE AND t.status != 'COMPLETED'")
    List<Task> findOverdueTasks();

    @Query("SELECT DISTINCT t FROM Task t " +
            "LEFT JOIN FETCH t.createdBy " +
            "LEFT JOIN FETCH t.assignedTo " +
            "LEFT JOIN FETCH t.approvedBy " +
            "LEFT JOIN FETCH t.dependsOn " +
            "LEFT JOIN FETCH t.legalCase c " +
            "LEFT JOIN FETCH c.owner " +
            "WHERE t.legalCase.id = :caseId AND t.dueDate < CURRENT_DATE AND t.status != 'COMPLETED'")
    List<Task> findOverdueTasksByCaseId(@Param("caseId") Long caseId);

    // ============================================
    // COUNT QUERIES (These don't need JOIN FETCH)
    // ============================================

    long countByLegalCaseAndType(LegalCase legalCase, TaskType type);
    long countByLegalCaseAndTypeAndStatus(LegalCase legalCase, TaskType type, TaskStatus status);
    long countByLegalCaseAndStatus(LegalCase legalCase, TaskStatus status);

    // ============================================
    // UPDATE QUERY
    // ============================================

    @Modifying
    @Query("UPDATE Task t SET t.progress = 100 WHERE t.id = :taskId AND t.status = 'COMPLETED'")
    void setProgressTo100OnCompletion(@Param("taskId") Long taskId);
}