package com.legalcase.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legalcase.dto.request.MarkNotificationsReadRequest;
import com.legalcase.dto.response.NotificationResponse;
import com.legalcase.enums.NotificationPriority;
import com.legalcase.enums.NotificationStatus;
import com.legalcase.enums.NotificationType;
import com.legalcase.security.JwtUtils;
import com.legalcase.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Notification Controller Unit Tests")
class NotificationControllerUnitTests {

    @Mock
    private NotificationService notificationService;

    @Mock
    private JwtUtils jwtUtils;

    @InjectMocks
    private NotificationController notificationController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private String mockToken = "Bearer mock.jwt.token";
    private Long mockUserId = 1L;

    private NotificationResponse mockNotificationResponse;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(notificationController).build();
        objectMapper = new ObjectMapper();

        // Setup mock response DTO
        mockNotificationResponse = NotificationResponse.builder()
                .id(1L)
                .type(NotificationType.TASK_ASSIGNED)
                .priority(NotificationPriority.MEDIUM)
                .status(NotificationStatus.UNREAD)
                .title("Task Assigned")
                .message("You have been assigned to task: Review documents")
                .taskId(1L)
                .taskTitle("Review documents")
                .actionUrl("/tasks/1")
                .createdAt(LocalDateTime.now())
                .build();
    }

    // ============================================
    // GET NOTIFICATIONS TESTS (Paginated - Returns DTOs)
    // ============================================

    @Test
    @DisplayName("GET /api/notifications - Should return paginated notifications as DTOs")
    void getNotifications_Success_Returns200() throws Exception {
        when(jwtUtils.getUserIdFromToken(anyString())).thenReturn(mockUserId);

        Page<NotificationResponse> notificationsPage = new PageImpl<>(
                Arrays.asList(mockNotificationResponse),
                PageRequest.of(0, 20),
                1
        );

        when(notificationService.getNotificationsForUser(eq(mockUserId), eq(0), eq(20)))
                .thenReturn(notificationsPage);

        mockMvc.perform(get("/notifications?page=0&size=20")
                        .header("Authorization", mockToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Task Assigned"))
                .andExpect(jsonPath("$.content[0].type").value("TASK_ASSIGNED"))
                .andExpect(jsonPath("$.content[0].status").value("UNREAD"));
    }

    // ============================================
    // GET UNREAD NOTIFICATIONS TESTS (Returns DTOs)
    // ============================================

    @Test
    @DisplayName("GET /api/notifications/unread - Should return unread notifications as DTOs")
    void getUnreadNotifications_Success_Returns200() throws Exception {
        when(jwtUtils.getUserIdFromToken(anyString())).thenReturn(mockUserId);

        List<NotificationResponse> unreadNotifications = Arrays.asList(mockNotificationResponse);
        when(notificationService.getUnreadNotifications(mockUserId))
                .thenReturn(unreadNotifications);

        mockMvc.perform(get("/notifications/unread")
                        .header("Authorization", mockToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].status").value("UNREAD"));
    }

    // ============================================
    // GET UNREAD COUNT TESTS
    // ============================================

    @Test
    @DisplayName("GET /api/notifications/unread/count - Should return unread count")
    void getUnreadCount_Success_Returns200() throws Exception {
        when(jwtUtils.getUserIdFromToken(anyString())).thenReturn(mockUserId);

        when(notificationService.getUnreadCount(mockUserId)).thenReturn(5L);

        mockMvc.perform(get("/notifications/unread/count")
                        .header("Authorization", mockToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(5));
    }

    // ============================================
    // MARK NOTIFICATIONS AS READ TESTS
    // ============================================

    @Test
    @DisplayName("PUT /api/notifications/read - Should return 200 when notifications marked as read")
    void markAsRead_Success_Returns200() throws Exception {
        when(jwtUtils.getUserIdFromToken(anyString())).thenReturn(mockUserId);

        MarkNotificationsReadRequest request = new MarkNotificationsReadRequest();
        request.setNotificationIds(Arrays.asList(1L, 2L, 3L));

        when(notificationService.markAsRead(anyList(), eq(mockUserId))).thenReturn(3);

        mockMvc.perform(put("/notifications/read")
                        .header("Authorization", mockToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(notificationService, times(1)).markAsRead(anyList(), eq(mockUserId));
    }

    @Test
    @DisplayName("PUT /api/notifications/read - Should return 200 with empty list")
    void markAsRead_EmptyList_Returns200() throws Exception {
        when(jwtUtils.getUserIdFromToken(anyString())).thenReturn(mockUserId);

        MarkNotificationsReadRequest request = new MarkNotificationsReadRequest();
        request.setNotificationIds(Arrays.asList());

        when(notificationService.markAsRead(anyList(), eq(mockUserId))).thenReturn(0);

        mockMvc.perform(put("/notifications/read")
                        .header("Authorization", mockToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    // ============================================
    // MARK ALL NOTIFICATIONS AS READ TESTS
    // ============================================

    @Test
    @DisplayName("PUT /api/notifications/read/all - Should return 200 when all notifications marked as read")
    void markAllAsRead_Success_Returns200() throws Exception {
        when(jwtUtils.getUserIdFromToken(anyString())).thenReturn(mockUserId);

        when(notificationService.markAllAsRead(mockUserId)).thenReturn(5);

        mockMvc.perform(put("/notifications/read/all")
                        .header("Authorization", mockToken))
                .andExpect(status().isOk());

        verify(notificationService, times(1)).markAllAsRead(mockUserId);
    }

    // ============================================
    // DIFFERENT NOTIFICATION TYPES TESTS
    // ============================================

    @Test
    @DisplayName("GET /api/notifications/unread - Should handle multiple notification types")
    void getUnreadNotifications_MultipleTypes_Returns200() throws Exception {
        when(jwtUtils.getUserIdFromToken(anyString())).thenReturn(mockUserId);

        // Create different types of notifications
        NotificationResponse taskAssigned = NotificationResponse.builder()
                .id(1L)
                .type(NotificationType.TASK_ASSIGNED)
                .title("Task Assigned")
                .build();

        NotificationResponse userMentioned = NotificationResponse.builder()
                .id(2L)
                .type(NotificationType.USER_MENTIONED)
                .title("You were mentioned")
                .build();

        NotificationResponse addedToCase = NotificationResponse.builder()
                .id(3L)
                .type(NotificationType.ADDED_TO_CASE)
                .title("Added to Case")
                .build();

        List<NotificationResponse> notifications = Arrays.asList(taskAssigned, userMentioned, addedToCase);
        when(notificationService.getUnreadNotifications(mockUserId)).thenReturn(notifications);

        mockMvc.perform(get("/notifications/unread")
                        .header("Authorization", mockToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("TASK_ASSIGNED"))
                .andExpect(jsonPath("$[1].type").value("USER_MENTIONED"))
                .andExpect(jsonPath("$[2].type").value("ADDED_TO_CASE"));
    }
}