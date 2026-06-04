package com.legalcase.repository;

import com.legalcase.entity.AuditLog;
import com.legalcase.enums.AuditAction;
import com.legalcase.enums.AuditStatus;
import com.legalcase.enums.EntityType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    // Find by user
    Page<AuditLog> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    // Find by action
    Page<AuditLog> findByActionOrderByCreatedAtDesc(AuditAction action, Pageable pageable);

    // Find by entity
    Page<AuditLog> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(EntityType entityType, Long entityId, Pageable pageable);

    // Find by date range
    @Query("SELECT a FROM AuditLog a WHERE a.createdAt BETWEEN :startDate AND :endDate ORDER BY a.createdAt DESC")
    Page<AuditLog> findByDateRange(@Param("startDate") LocalDateTime startDate,
                                   @Param("endDate") LocalDateTime endDate,
                                   Pageable pageable);

    // Advanced search with filters
    @Query("SELECT a FROM AuditLog a WHERE " +
            "(:userId IS NULL OR a.userId = :userId) AND " +
            "(:action IS NULL OR a.action = :action) AND " +
            "(:entityType IS NULL OR a.entityType = :entityType) AND " +
            "(:entityId IS NULL OR a.entityId = :entityId) AND " +
            "(:status IS NULL OR a.status = :status) AND " +
            "(a.createdAt BETWEEN :startDate AND :endDate) " +
            "ORDER BY a.createdAt DESC")
    Page<AuditLog> search(@Param("userId") Long userId,
                          @Param("action") AuditAction action,
                          @Param("entityType") EntityType entityType,
                          @Param("entityId") Long entityId,
                          @Param("status") AuditStatus status,
                          @Param("startDate") LocalDateTime startDate,
                          @Param("endDate") LocalDateTime endDate,
                          Pageable pageable);

    // Count by user
    long countByUserId(Long userId);

    // Count by action
    long countByAction(AuditAction action);

    // Get recent failed actions
    @Query("SELECT a FROM AuditLog a WHERE a.status = 'FAILURE' ORDER BY a.createdAt DESC")
    List<AuditLog> findRecentFailures(Pageable pageable);

    // Clean up old logs - FIXED: return long instead of void
    @Modifying
    @Query("DELETE FROM AuditLog a WHERE a.createdAt < :cutoffDate")
    long deleteOlderThan(@Param("cutoffDate") LocalDateTime cutoffDate);
}