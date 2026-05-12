package com.legalcase.repository;

import com.legalcase.entity.LegalCase;
import com.legalcase.enums.CaseStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface CaseRepository extends JpaRepository<LegalCase, Long> {

    // Basic queries
    List<LegalCase> findByOwnerId(Long ownerId);

    List<LegalCase> findByStatus(CaseStatus status);

    List<LegalCase> findByIsLocked(boolean isLocked);

    // Find cases by due date (exact match)
    List<LegalCase> findByDueDate(LocalDate dueDate);

    boolean existsByCaseNumber(String caseNumber);

    List<LegalCase> findByTitleContainingIgnoreCase(String title);

    @Query("SELECT c FROM LegalCase c JOIN CaseMember cm ON cm.legalCase = c WHERE cm.user.id = :userId")
    List<LegalCase> findCasesByMemberId(@Param("userId") Long userId);

    // ===== JOIN FETCH methods to prevent LazyInitializationException =====

    // Fetch case with owner only (assignedUsers is not a direct field)
    @Query("SELECT DISTINCT c FROM LegalCase c " +
            "LEFT JOIN FETCH c.owner " +
            "WHERE c.id = :caseId")
    Optional<LegalCase> findByIdWithDetails(@Param("caseId") Long caseId);

    @Query("SELECT DISTINCT c FROM LegalCase c " +
            "LEFT JOIN FETCH c.owner " +
            "WHERE c.owner.id = :ownerId")
    List<LegalCase> findByOwnerIdWithDetails(@Param("ownerId") Long ownerId);

    @Query("SELECT DISTINCT c FROM LegalCase c " +
            "LEFT JOIN FETCH c.owner " +
            "WHERE c.id IN (SELECT cm.legalCase.id FROM CaseMember cm WHERE cm.user.id = :userId)")
    List<LegalCase> findCasesByMemberIdWithDetails(@Param("userId") Long userId);

    @Query("SELECT DISTINCT c FROM LegalCase c " +
            "LEFT JOIN FETCH c.owner " +
            "WHERE c.status = :status")
    List<LegalCase> findByStatusWithDetails(@Param("status") CaseStatus status);

    // Task count queries for case progress
    @Query("SELECT COUNT(t) FROM Task t WHERE t.legalCase.id = :caseId AND t.type = 'MANDATORY'")
    long countMandatoryTasksByCaseId(@Param("caseId") Long caseId);

    @Query("SELECT COUNT(t) FROM Task t WHERE t.legalCase.id = :caseId AND t.type = 'MANDATORY' AND t.status = 'COMPLETED'")
    long countCompletedMandatoryTasksByCaseId(@Param("caseId") Long caseId);
}