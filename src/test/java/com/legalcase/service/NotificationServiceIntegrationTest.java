package com.legalcase.service;

import com.legalcase.entity.Notification;
import com.legalcase.entity.User;
import com.legalcase.enums.NotificationPriority;
import com.legalcase.enums.NotificationStatus;
import com.legalcase.enums.NotificationType;
import com.legalcase.enums.Role;
import com.legalcase.repository.NotificationRepository;
import com.legalcase.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@DisplayName("Notification Service Integration Tests")
class NotificationServiceIntegrationTest {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        // Create a test user
        testUser = new User();
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");
        testUser.setPassword("encoded");
        testUser.setFullName("Test User");
        testUser.setRole(Role.STAFF);
        testUser.setActive(true);
        testUser = userRepository.save(testUser);
    }

    @Test
    @DisplayName("Should create and retrieve notification")
    void testCreateAndRetrieveNotification() {
        // Create notification
        Notification notification = notificationService.createNotification(
                testUser.getId(),
                NotificationType.TASK_ASSIGNED,
                NotificationPriority.MEDIUM,
                "Task Assigned",
                "You have been assigned to a task",
                1L,  // caseId
                1L,  // taskId
                null, // messageId
                2L,  // actorId
                "/tasks/1"
        );

        // Verify
        assertThat(notification).isNotNull();
        assertThat(notification.getId()).isNotNull();
        assertThat(notification.getType()).isEqualTo(NotificationType.TASK_ASSIGNED);
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.UNREAD);

        // Retrieve unread count
        long unreadCount = notificationService.getUnreadCount(testUser.getId());
        assertThat(unreadCount).isEqualTo(1);
    }
}

