package com.legalcase.repository;

import com.legalcase.entity.Notification;
import com.legalcase.enums.NotificationStatus;
import com.legalcase.enums.NotificationType;
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
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // ===== FIND METHODS WITH ENTITY GRAPH =====

    @Query("SELECT DISTINCT n FROM Notification n " +
            "LEFT JOIN FETCH n.user " +
            "WHERE n.user.id = :userId " +
            "ORDER BY n.createdAt DESC")
    Page<Notification> findByUserIdWithDetails(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT DISTINCT n FROM Notification n " +
            "LEFT JOIN FETCH n.user " +
            "WHERE n.user.id = :userId AND n.status = 'UNREAD' " +
            "ORDER BY n.createdAt DESC")
    List<Notification> findUnreadByUserIdWithDetails(@Param("userId") Long userId);

    @Query("SELECT DISTINCT n FROM Notification n " +
            "LEFT JOIN FETCH n.user " +
            "WHERE n.user.id = :userId AND n.status = 'ARCHIVED' " +
            "ORDER BY n.createdAt DESC")
    Page<Notification> findArchivedByUserIdWithDetails(@Param("userId") Long userId, Pageable pageable);

    // ===== COUNT METHODS =====

    long countByUserIdAndStatus(@Param("userId") Long userId, @Param("status") NotificationStatus status);

    @Query("SELECT COUNT(n) FROM Notification n WHERE n.user.id = :userId AND n.status = 'UNREAD' AND n.priority = 'URGENT'")
    long countUrgentUnreadByUserId(@Param("userId") Long userId);

    // ===== MARK AS READ/ARCHIVED =====

    @Modifying
    @Query("UPDATE Notification n SET n.status = 'READ', n.readAt = :readAt WHERE n.id IN :notificationIds AND n.user.id = :userId")
    int markAsRead(@Param("notificationIds") List<Long> notificationIds,
                   @Param("userId") Long userId,
                   @Param("readAt") LocalDateTime readAt);

    @Modifying
    @Query("UPDATE Notification n SET n.status = 'READ', n.readAt = :readAt WHERE n.user.id = :userId AND n.status = 'UNREAD'")
    int markAllAsRead(@Param("userId") Long userId, @Param("readAt") LocalDateTime readAt);

    @Modifying
    @Query("UPDATE Notification n SET n.status = 'ARCHIVED', n.archivedAt = :archivedAt WHERE n.id IN :notificationIds AND n.user.id = :userId")
    int archive(@Param("notificationIds") List<Long> notificationIds,
                @Param("userId") Long userId,
                @Param("archivedAt") LocalDateTime archivedAt);

    // ===== DELETE METHODS =====

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.id IN :notificationIds AND n.user.id = :userId")
    int deleteByIds(@Param("notificationIds") List<Long> notificationIds, @Param("userId") Long userId);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.createdAt < :cutoffDate AND (n.status = 'READ' OR n.status = 'ARCHIVED')")
    int deleteOldReadAndArchivedNotifications(@Param("cutoffDate") LocalDateTime cutoffDate);

    // ===== BULK OPERATIONS FOR CLEANUP =====

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.caseId = :caseId")
    int deleteByCaseId(@Param("caseId") Long caseId);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.taskId = :taskId")
    int deleteByTaskId(@Param("taskId") Long taskId);
}