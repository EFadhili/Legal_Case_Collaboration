package com.legalcase.repository;

import com.legalcase.entity.LegalCase;
import com.legalcase.enums.CasePriority;
import com.legalcase.enums.CaseStatus;
import com.legalcase.enums.CaseType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CaseRepository extends JpaRepository<LegalCase, Long> {

    // ============================================
    // BASIC FIND METHODS (WITH JOIN FETCH)
    // ============================================

    @Override
    @Query("SELECT DISTINCT c FROM LegalCase c " +
            "LEFT JOIN FETCH c.owner " +
            "WHERE c.id = :id")
    Optional<LegalCase> findById(@Param("id") Long id);

    @Query("SELECT DISTINCT c FROM LegalCase c " +
            "LEFT JOIN FETCH c.owner " +
            "WHERE c.caseNumber = :caseNumber")
    Optional<LegalCase> findByCaseNumberWithDetails(@Param("caseNumber") String caseNumber);

    @Query("SELECT DISTINCT c FROM LegalCase c " +
            "LEFT JOIN FETCH c.owner " +
            "WHERE c.owner.id = :ownerId")
    List<LegalCase> findByOwnerIdWithDetails(@Param("ownerId") Long ownerId);

    @Query("SELECT DISTINCT c FROM LegalCase c " +
            "LEFT JOIN FETCH c.owner " +
            "WHERE c.status = :status")
    List<LegalCase> findByStatusWithDetails(@Param("status") CaseStatus status);

    @Query("SELECT DISTINCT c FROM LegalCase c " +
            "LEFT JOIN FETCH c.owner " +
            "WHERE c.priority = :priority")
    List<LegalCase> findByPriorityWithDetails(@Param("priority") CasePriority priority);

    @Query("SELECT DISTINCT c FROM LegalCase c " +
            "LEFT JOIN FETCH c.owner " +
            "WHERE c.type = :type")
    List<LegalCase> findByTypeWithDetails(@Param("type") CaseType type);

    @Query("SELECT DISTINCT c FROM LegalCase c " +
            "LEFT JOIN FETCH c.owner " +
            "WHERE c.isLocked = :isLocked")
    List<LegalCase> findByIsLockedWithDetails(@Param("isLocked") boolean isLocked);

    // ===== DUE DATE METHODS =====

    @Query("SELECT DISTINCT c FROM LegalCase c " +
            "LEFT JOIN FETCH c.owner " +
            "WHERE c.dueDate = :dueDate")
    List<LegalCase> findByDueDateWithDetails(@Param("dueDate") LocalDate dueDate);

    @Query("SELECT DISTINCT c FROM LegalCase c " +
            "LEFT JOIN FETCH c.owner " +
            "WHERE c.dueDate < :date")
    List<LegalCase> findByDueDateBeforeWithDetails(@Param("date") LocalDate date);

    @Query("SELECT DISTINCT c FROM LegalCase c " +
            "LEFT JOIN FETCH c.owner " +
            "WHERE c.dueDate > :date")
    List<LegalCase> findByDueDateAfterWithDetails(@Param("date") LocalDate date);

    @Query("SELECT DISTINCT c FROM LegalCase c " +
            "LEFT JOIN FETCH c.owner " +
            "WHERE c.dueDate BETWEEN :startDate AND :endDate")
    List<LegalCase> findByDueDateBetweenWithDetails(@Param("startDate") LocalDate startDate,
                                                    @Param("endDate") LocalDate endDate);

    // ===== SIMPLE DUE DATE METHODS (For scheduler - no JOIN FETCH needed for counts) =====

    List<LegalCase> findByDueDate(LocalDate dueDate);

    List<LegalCase> findByDueDateBefore(LocalDate date);

    List<LegalCase> findByDueDateAfter(LocalDate date);

    // ===== CASE MEMBER RELATED QUERIES =====

    @Query("SELECT DISTINCT c FROM LegalCase c " +
            "LEFT JOIN FETCH c.owner " +
            "WHERE c.id IN (SELECT cm.legalCase.id FROM CaseMember cm WHERE cm.user.id = :userId)")
    List<LegalCase> findCasesByMemberIdWithDetails(@Param("userId") Long userId);

    // Simple version for ChatService (returns just IDs or basic info)
    @Query("SELECT c FROM LegalCase c WHERE c.id IN (SELECT cm.legalCase.id FROM CaseMember cm WHERE cm.user.id = :userId)")
    List<LegalCase> findCasesByMemberId(@Param("userId") Long userId);

    // ============================================
    // SEARCH METHODS
    // ============================================

    @Query("SELECT DISTINCT c FROM LegalCase c " +
            "LEFT JOIN FETCH c.owner " +
            "WHERE LOWER(c.title) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
            "OR LOWER(c.caseNumber) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
            "OR LOWER(c.description) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    List<LegalCase> searchAllCases(@Param("searchTerm") String searchTerm);

    @Query("SELECT DISTINCT c FROM LegalCase c " +
            "LEFT JOIN FETCH c.owner " +
            "WHERE c.id IN (SELECT cm.legalCase.id FROM CaseMember cm WHERE cm.user.id = :userId) " +
            "AND (LOWER(c.title) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
            "OR LOWER(c.caseNumber) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
            "OR LOWER(c.description) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
    List<LegalCase> searchUserCases(@Param("userId") Long userId, @Param("searchTerm") String searchTerm);

    @Query("SELECT DISTINCT c FROM LegalCase c " +
            "LEFT JOIN FETCH c.owner " +
            "WHERE LOWER(c.title) LIKE LOWER(CONCAT('%', :title, '%'))")
    List<LegalCase> findByTitleContainingWithDetails(@Param("title") String title);

    // ============================================
    // COUNT & EXISTENCE METHODS
    // ============================================

    boolean existsByCaseNumber(String caseNumber);

    @Query("SELECT COUNT(c) FROM LegalCase c WHERE c.status = :status")
    long countByStatus(@Param("status") CaseStatus status);

    @Query("SELECT COUNT(c) FROM LegalCase c WHERE c.priority = :priority")
    long countByPriority(@Param("priority") CasePriority priority);

    // ============================================
    // TASK COUNT QUERIES (For case progress)
    // ============================================

    @Query("SELECT COUNT(t) FROM Task t WHERE t.legalCase.id = :caseId AND t.type = 'MANDATORY'")
    long countMandatoryTasksByCaseId(@Param("caseId") Long caseId);

    @Query("SELECT COUNT(t) FROM Task t WHERE t.legalCase.id = :caseId AND t.type = 'MANDATORY' AND t.status = 'COMPLETED'")
    long countCompletedMandatoryTasksByCaseId(@Param("caseId") Long caseId);

    // ============================================
    // PAGINATED QUERIES
    // ============================================

    @Query("SELECT DISTINCT c FROM LegalCase c " +
            "LEFT JOIN FETCH c.owner " +
            "WHERE c.id IN (SELECT cm.legalCase.id FROM CaseMember cm WHERE cm.user.id = :userId)")
    Page<LegalCase> findMyCasesPaged(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT DISTINCT c FROM LegalCase c " +
            "LEFT JOIN FETCH c.owner")
    Page<LegalCase> findAllPaged(Pageable pageable);


    // Soft delete by ID
    @Modifying
    @Query("UPDATE LegalCase c SET c.isDeleted = true, c.deletedAt = CURRENT_TIMESTAMP WHERE c.id = :caseId")
    void softDeleteById(@Param("caseId") Long caseId);

    // Soft delete by case number
    @Modifying
    @Query("UPDATE LegalCase c SET c.isDeleted = true, c.deletedAt = CURRENT_TIMESTAMP WHERE c.caseNumber = :caseNumber")
    void softDeleteByCaseNumber(@Param("caseNumber") String caseNumber);

    // Find active cases (not deleted) - override default findById
    @Query("SELECT DISTINCT c FROM LegalCase c " +
            "LEFT JOIN FETCH c.owner " +
            "WHERE c.id = :id AND c.isDeleted = false")
    Optional<LegalCase> findActiveById(@Param("id") Long id);

    // Find active cases by case number
    @Query("SELECT DISTINCT c FROM LegalCase c " +
            "LEFT JOIN FETCH c.owner " +
            "WHERE c.caseNumber = :caseNumber AND c.isDeleted = false")
    Optional<LegalCase> findActiveByCaseNumber(@Param("caseNumber") String caseNumber);

    // Restore a soft-deleted case
    @Modifying
    @Query("UPDATE LegalCase c SET c.isDeleted = false, c.deletedAt = null WHERE c.id = :caseId")
    void restoreById(@Param("caseId") Long caseId);

    // Permanently delete (hard delete) soft-deleted cases older than cutoff
    @Modifying
    @Query("DELETE FROM LegalCase c WHERE c.isDeleted = true AND c.deletedAt < :cutoffDate")
    int permanentlyDeleteOldCases(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Find all soft-deleted cases with details.
     */
    @Query("SELECT DISTINCT c FROM LegalCase c " +
            "LEFT JOIN FETCH c.owner " +
            "WHERE c.isDeleted = true")
    List<LegalCase> findByIsDeletedTrueWithDetails();
}