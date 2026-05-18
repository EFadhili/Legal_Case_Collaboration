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

    // ===== EXISTING METHODS =====

    Page<Notification> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    List<Notification> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, NotificationStatus status);

    long countByUserIdAndStatus(Long userId, NotificationStatus status);

    List<Notification> findByUserIdAndType(Long userId, NotificationType type);

    @Modifying
    @Query("UPDATE Notification n SET n.status = 'READ', n.readAt = :readAt WHERE n.id IN :notificationIds AND n.user.id = :userId")
    int markAsRead(@Param("notificationIds") List<Long> notificationIds,
                   @Param("userId") Long userId,
                   @Param("readAt") LocalDateTime readAt);

    @Modifying
    @Query("UPDATE Notification n SET n.status = 'READ', n.readAt = :readAt WHERE n.user.id = :userId AND n.status = 'UNREAD'")
    int markAllAsRead(@Param("userId") Long userId, @Param("readAt") LocalDateTime readAt);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.createdAt < :cutoffDate AND n.status = 'READ'")
    int deleteOldReadNotifications(@Param("cutoffDate") LocalDateTime cutoffDate);

    // ===== NEW JOIN FETCH METHODS (Prevent LazyInitializationException) =====

    /**
     * Get paginated notifications for a user with all associations initialized
     */
    @Query("SELECT DISTINCT n FROM Notification n " +
            "LEFT JOIN FETCH n.user " +
            "WHERE n.user.id = :userId " +
            "ORDER BY n.createdAt DESC")
    Page<Notification> findByUserIdWithDetails(@Param("userId") Long userId, Pageable pageable);

    /**
     * Get unread notifications for a user with associations initialized
     */
    @Query("SELECT DISTINCT n FROM Notification n " +
            "LEFT JOIN FETCH n.user " +
            "WHERE n.user.id = :userId AND n.status = 'UNREAD' " +
            "ORDER BY n.createdAt DESC")
    List<Notification> findUnreadByUserIdWithDetails(@Param("userId") Long userId);
}