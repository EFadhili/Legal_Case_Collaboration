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

    // Get all notifications for a user (paginated)
    Page<Notification> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    // Get unread notifications for a user
    List<Notification> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, NotificationStatus status);

    // Count unread notifications for a user
    long countByUserIdAndStatus(Long userId, NotificationStatus status);

    // Get notifications by type
    List<Notification> findByUserIdAndType(Long userId, NotificationType type);

    // Mark notifications as read
    @Modifying
    @Query("UPDATE Notification n SET n.status = 'READ', n.readAt = :readAt WHERE n.id IN :notificationIds AND n.user.id = :userId")
    int markAsRead(@Param("notificationIds") List<Long> notificationIds,
                   @Param("userId") Long userId,
                   @Param("readAt") LocalDateTime readAt);

    // Mark all notifications as read for a user
    @Modifying
    @Query("UPDATE Notification n SET n.status = 'READ', n.readAt = :readAt WHERE n.user.id = :userId AND n.status = 'UNREAD'")
    int markAllAsRead(@Param("userId") Long userId, @Param("readAt") LocalDateTime readAt);

    // Delete old notifications (older than days)
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.createdAt < :cutoffDate AND n.status = 'READ'")
    int deleteOldReadNotifications(@Param("cutoffDate") LocalDateTime cutoffDate);
}