package com.legalcase.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.legalcase.dto.request.CreateTaskRequest;
import com.legalcase.dto.request.UpdateTaskProgressRequest;
import com.legalcase.dto.request.UpdateTaskStatusRequest;
import com.legalcase.entity.LegalCase;
import com.legalcase.entity.Task;
import com.legalcase.entity.User;
import com.legalcase.enums.*;
import com.legalcase.security.JwtUtils;
import com.legalcase.service.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Task Controller Unit Tests")
class TaskControllerUnitTests {

    @Mock
    private TaskService taskService;

    @Mock
    private JwtUtils jwtUtils;

    @InjectMocks
    private TaskController taskController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private CreateTaskRequest createTaskRequest;
    private UpdateTaskStatusRequest updateStatusRequest;
    private UpdateTaskProgressRequest updateProgressRequest;
    private Task mockTask;
    private LegalCase mockLegalCase;
    private User mockAssignedUser;
    private User mockCreatedBy;
    private String mockToken = "Bearer mock.jwt.token";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(taskController).build();

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        mockAssignedUser = new User();
        mockAssignedUser.setId(2L);
        mockAssignedUser.setFullName("Staff User");
        mockAssignedUser.setEmail("staff@test.com");
        mockAssignedUser.setRole(Role.STAFF);

        mockCreatedBy = new User();
        mockCreatedBy.setId(1L);
        mockCreatedBy.setFullName("Lawyer User");
        mockCreatedBy.setEmail("lawyer@test.com");
        mockCreatedBy.setRole(Role.LAWYER);

        mockLegalCase = new LegalCase();
        mockLegalCase.setId(1L);
        mockLegalCase.setCaseNumber("CASE-2026-00001");
        mockLegalCase.setTitle("Test Case");
        mockLegalCase.setStatus(CaseStatus.IN_PROGRESS);
        mockLegalCase.setLocked(false);

        mockTask = new Task();
        mockTask.setId(1L);
        mockTask.setTitle("Test Task");
        mockTask.setDescription("Test Description");
        mockTask.setType(TaskType.MANDATORY);
        mockTask.setPriority(TaskPriority.HIGH);
        mockTask.setStatus(TaskStatus.TODO);
        mockTask.setProgress(0);
        mockTask.setDueDate(LocalDate.now().plusDays(7));
        mockTask.setLegalCase(mockLegalCase);
        mockTask.setAssignedTo(mockAssignedUser);
        mockTask.setCreatedBy(mockCreatedBy);
        mockTask.setCreatedAt(LocalDateTime.now());
        mockTask.setUpdatedAt(LocalDateTime.now());

        createTaskRequest = new CreateTaskRequest();
        createTaskRequest.setTitle("Test Task");
        createTaskRequest.setDescription("Test Description");
        createTaskRequest.setType(TaskType.MANDATORY);
        createTaskRequest.setPriority(TaskPriority.HIGH);
        createTaskRequest.setDueDate(LocalDate.now().plusDays(7));
        createTaskRequest.setAssignedToUserId(2L);

        updateStatusRequest = new UpdateTaskStatusRequest();
        updateStatusRequest.setStatus(TaskStatus.IN_PROGRESS);

        updateProgressRequest = new UpdateTaskProgressRequest();
        updateProgressRequest.setProgress(50);
    }

    @Test
    @DisplayName("POST /api/tasks/case/{caseId} - Should return 201 when task is created")
    void createTask_Success_Returns201() throws Exception {
        when(jwtUtils.getUserIdFromToken(anyString())).thenReturn(1L);

        // Use any() for ALL parameters to match any invocation
        when(taskService.createTask(
                anyString(),
                anyString(),
                any(TaskType.class),
                any(TaskPriority.class),
                any(),
                anyLong(),
                anyLong(),
                anyLong(),
                nullable(Long.class)
        )).thenReturn(mockTask);

        mockMvc.perform(post("/tasks/case/1")
                        .header("Authorization", mockToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createTaskRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("Test Task"))
                .andExpect(jsonPath("$.type").value("MANDATORY"))
                .andExpect(jsonPath("$.status").value("TODO"))
                .andExpect(jsonPath("$.assignedToName").value("Staff User"))
                .andExpect(jsonPath("$.createdByName").value("Lawyer User"));

        verify(taskService, times(1)).createTask(
                anyString(),
                anyString(),
                any(TaskType.class),
                any(TaskPriority.class),
                any(),
                anyLong(),
                anyLong(),
                anyLong(),
                nullable(Long.class)
        );
    }

    @Test
    @DisplayName("GET /api/tasks/case/{caseId} - Should return list of tasks")
    void getTasksByCase_Success_Returns200() throws Exception {
        List<Task> tasks = Arrays.asList(mockTask);
        when(taskService.getTasksByCase(1L)).thenReturn(tasks);

        mockMvc.perform(get("/tasks/case/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].title").value("Test Task"))
                .andExpect(jsonPath("$[0].status").value("TODO"));

        verify(taskService, times(1)).getTasksByCase(1L);
    }

    @Test
    @DisplayName("GET /api/tasks/{id} - Should return 200 when task exists")
    void getTaskById_Success_Returns200() throws Exception {
        when(taskService.findById(1L)).thenReturn(mockTask);

        mockMvc.perform(get("/tasks/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("Test Task"))
                .andExpect(jsonPath("$.status").value("TODO"));

        verify(taskService, times(1)).findById(1L);
    }

    @Test
    @DisplayName("GET /api/tasks/{id} - Should return 404 when task doesn't exist")
    void getTaskById_NotFound_Returns404() throws Exception {
        when(taskService.findById(999L))
                .thenThrow(new RuntimeException("Task not found with ID: 999"));

        mockMvc.perform(get("/tasks/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PATCH /api/tasks/{id}/status - Should return 200 when status updated")
    void updateStatus_Success_Returns200() throws Exception {
        mockTask.setStatus(TaskStatus.IN_PROGRESS);

        when(jwtUtils.getUserIdFromToken(anyString())).thenReturn(2L);
        when(taskService.updateStatus(eq(1L), eq(TaskStatus.IN_PROGRESS), eq(2L)))
                .thenReturn(mockTask);

        mockMvc.perform(patch("/tasks/1/status")
                        .header("Authorization", mockToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateStatusRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        verify(taskService, times(1)).updateStatus(eq(1L), eq(TaskStatus.IN_PROGRESS), eq(2L));
    }

    @Test
    @DisplayName("PATCH /api/tasks/{id}/progress - Should return 200 when progress updated")
    void updateProgress_Success_Returns200() throws Exception {
        mockTask.setProgress(50);
        mockTask.setStatus(TaskStatus.IN_PROGRESS);

        when(jwtUtils.getUserIdFromToken(anyString())).thenReturn(2L);
        when(taskService.updateProgress(eq(1L), eq(50), eq(2L)))
                .thenReturn(mockTask);

        mockMvc.perform(patch("/tasks/1/progress")
                        .header("Authorization", mockToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateProgressRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progress").value(50))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        verify(taskService, times(1)).updateProgress(eq(1L), eq(50), eq(2L));
    }

    @Test
    @DisplayName("PATCH /api/tasks/{id}/progress - Should auto-move to REVIEW at 100%")
    void updateProgress_To100_AutoMovesToReview() throws Exception {
        UpdateTaskProgressRequest completeRequest = new UpdateTaskProgressRequest();
        completeRequest.setProgress(100);

        mockTask.setProgress(100);
        mockTask.setStatus(TaskStatus.REVIEW);

        when(jwtUtils.getUserIdFromToken(anyString())).thenReturn(2L);
        when(taskService.updateProgress(eq(1L), eq(100), eq(2L)))
                .thenReturn(mockTask);

        mockMvc.perform(patch("/tasks/1/progress")
                        .header("Authorization", mockToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(completeRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progress").value(100))
                .andExpect(jsonPath("$.status").value("REVIEW"));
    }

    @Test
    @DisplayName("PATCH /api/tasks/{id}/assign - Should return 200 when task assigned")
    void assignTask_Success_Returns200() throws Exception {
        when(jwtUtils.getUserIdFromToken(anyString())).thenReturn(1L);
        when(taskService.assignTask(eq(1L), eq(3L), eq(1L)))
                .thenReturn(mockTask);

        mockMvc.perform(patch("/tasks/1/assign?assignedToUserId=3")
                        .header("Authorization", mockToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedToId").value(2))
                .andExpect(jsonPath("$.assignedToName").value("Staff User"));

        verify(taskService, times(1)).assignTask(eq(1L), eq(3L), eq(1L));
    }

    @Test
    @DisplayName("GET /api/tasks/my-tasks - Should return tasks assigned to current user")
    void getMyTasks_Success_Returns200() throws Exception {
        List<Task> tasks = Arrays.asList(mockTask);

        when(jwtUtils.getUserIdFromToken(anyString())).thenReturn(2L);
        when(taskService.getTasksByAssignedUser(2L)).thenReturn(tasks);

        mockMvc.perform(get("/tasks/my-tasks")
                        .header("Authorization", mockToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].title").value("Test Task"))
                .andExpect(jsonPath("$[0].assignedToId").value(2));

        verify(taskService, times(1)).getTasksByAssignedUser(2L);
    }

    @Test
    @DisplayName("GET /api/tasks/case/{caseId}/overdue - Should return overdue tasks")
    void getOverdueTasks_Success_Returns200() throws Exception {
        List<Task> overdueTasks = Arrays.asList(mockTask);

        when(taskService.getOverdueTasksByCase(1L)).thenReturn(overdueTasks);

        mockMvc.perform(get("/tasks/case/1/overdue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));

        verify(taskService, times(1)).getOverdueTasksByCase(1L);
    }

    @Test
    @DisplayName("GET /api/tasks/case/{caseId}/unblocked - Should return unblocked tasks")
    void getUnblockedTasks_Success_Returns200() throws Exception {
        List<Task> unblockedTasks = Arrays.asList(mockTask);

        when(taskService.getUnblockedTasksByCase(1L)).thenReturn(unblockedTasks);

        mockMvc.perform(get("/tasks/case/1/unblocked"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));

        verify(taskService, times(1)).getUnblockedTasksByCase(1L);
    }
}
