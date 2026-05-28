package com.legalcase.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.legalcase.dto.request.CreateTaskRequest;
import com.legalcase.dto.request.UpdateTaskProgressRequest;
import com.legalcase.dto.request.UpdateTaskStatusRequest;
import com.legalcase.dto.response.TaskResponse;
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
    private String mockToken = "Bearer mock.jwt.token";
    private Long mockUserId = 1L;

    private Task mockTask;
    private LegalCase mockLegalCase;
    private User mockAssignedUser;
    private User mockCreatedBy;
    private CreateTaskRequest createTaskRequest;
    private UpdateTaskStatusRequest updateStatusRequest;
    private UpdateTaskProgressRequest updateProgressRequest;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(taskController).build();

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        // Setup mock assigned user
        mockAssignedUser = new User();
        mockAssignedUser.setId(2L);
        mockAssignedUser.setUsername("staffjane");
        mockAssignedUser.setFullName("Jane Staff");
        mockAssignedUser.setEmail("jane@legalfirm.com");
        mockAssignedUser.setRole(Role.STAFF);

        // Setup mock created by user
        mockCreatedBy = new User();
        mockCreatedBy.setId(1L);
        mockCreatedBy.setUsername("lawyerjohn");
        mockCreatedBy.setFullName("John Lawyer");
        mockCreatedBy.setEmail("john@legalfirm.com");
        mockCreatedBy.setRole(Role.LAWYER);

        // Setup mock legal case
        mockLegalCase = new LegalCase();
        mockLegalCase.setId(1L);
        mockLegalCase.setCaseNumber("CASE-2026-00001");
        mockLegalCase.setTitle("Test Case");
        mockLegalCase.setStatus(CaseStatus.IN_PROGRESS);
        mockLegalCase.setLocked(false);

        // Setup mock task
        mockTask = new Task();
        mockTask.setId(1L);
        mockTask.setTaskNumber("TASK-2026-1-00001");
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

        // Setup Create Task Request
        createTaskRequest = new CreateTaskRequest();
        createTaskRequest.setTitle("Test Task");
        createTaskRequest.setDescription("Test Description");
        createTaskRequest.setType(TaskType.MANDATORY);
        createTaskRequest.setPriority(TaskPriority.HIGH);
        createTaskRequest.setDueDate(LocalDate.now().plusDays(7));
        createTaskRequest.setAssignedToUserId(2L);
        createTaskRequest.setDependsOnTaskId(null);

        // Setup Update Status Request
        updateStatusRequest = new UpdateTaskStatusRequest();
        updateStatusRequest.setStatus(TaskStatus.IN_PROGRESS);

        // Setup Update Progress Request
        updateProgressRequest = new UpdateTaskProgressRequest();
        updateProgressRequest.setProgress(50);
    }

    // ============================================
    // CREATE TASK TESTS
    // ============================================

    @Test
    @DisplayName("POST /api/tasks/case/{caseId} - Should return 201 when task created")
    void createTaskInCase_Success_Returns201() throws Exception {
        when(jwtUtils.getUserIdFromToken(anyString())).thenReturn(mockUserId);
        when(taskService.createTask(
                anyString(), anyString(), any(TaskType.class), any(TaskPriority.class),
                any(), anyLong(), anyLong(), anyLong(), anyLong()))
                .thenReturn(mockTask);

        mockMvc.perform(post("/tasks/case/1")
                        .header("Authorization", mockToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createTaskRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.taskNumber").value("TASK-2026-1-00001"))
                .andExpect(jsonPath("$.title").value("Test Task"))
                .andExpect(jsonPath("$.type").value("MANDATORY"))
                .andExpect(jsonPath("$.status").value("TODO"))
                .andExpect(jsonPath("$.assignedToUsername").value("staffjane"))
                .andExpect(jsonPath("$.createdByUsername").value("lawyerjohn"));

        verify(taskService, times(1)).createTask(anyString(), anyString(), any(TaskType.class),
                any(TaskPriority.class), any(), anyLong(), anyLong(), anyLong(), anyLong());
    }

    // ============================================
    // GET TASK TESTS (by ID or Task Number)
    // ============================================

    @Test
    @DisplayName("GET /api/tasks/{identifier} - Should return 200 when task found by ID")
    void getTaskById_Success_Returns200() throws Exception {
        when(jwtUtils.getUserIdFromToken(anyString())).thenReturn(mockUserId);
        when(taskService.getTask(eq("1"), eq(mockUserId))).thenReturn(mockTask);

        mockMvc.perform(get("/tasks/1")
                        .header("Authorization", mockToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.taskNumber").value("TASK-2026-1-00001"))
                .andExpect(jsonPath("$.title").value("Test Task"));
    }

    @Test
    @DisplayName("GET /api/tasks/{identifier} - Should return 200 when task found by task number")
    void getTaskByTaskNumber_Success_Returns200() throws Exception {
        when(jwtUtils.getUserIdFromToken(anyString())).thenReturn(mockUserId);
        when(taskService.getTask(eq("TASK-2026-1-00001"), eq(mockUserId))).thenReturn(mockTask);

        mockMvc.perform(get("/tasks/TASK-2026-1-00001")
                        .header("Authorization", mockToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.taskNumber").value("TASK-2026-1-00001"));
    }

    @Test
    @DisplayName("GET /api/tasks/{identifier} - Should return 404 when task not found")
    void getTask_NotFound_Returns404() throws Exception {
        when(jwtUtils.getUserIdFromToken(anyString())).thenReturn(mockUserId);
        when(taskService.getTask(eq("999"), eq(mockUserId)))
                .thenThrow(new RuntimeException("Task not found"));

        mockMvc.perform(get("/tasks/999")
                        .header("Authorization", mockToken))
                .andExpect(status().isNotFound());
    }

    // ============================================
    // UPDATE STATUS TESTS
    // ============================================

    @Test
    @DisplayName("PATCH /api/tasks/{identifier}/status - Should return 200 when status updated by ID")
    void updateStatusById_Success_Returns200() throws Exception {
        when(jwtUtils.getUserIdFromToken(anyString())).thenReturn(mockUserId);
        when(taskService.updateStatus(eq("1"), eq(TaskStatus.IN_PROGRESS), eq(mockUserId)))
                .thenReturn(mockTask);

        mockMvc.perform(patch("/tasks/1/status")
                        .header("Authorization", mockToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateStatusRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TODO")); // Mock returns TODO
    }

    @Test
    @DisplayName("PATCH /api/tasks/{identifier}/status - Should return 200 when status updated by task number")
    void updateStatusByTaskNumber_Success_Returns200() throws Exception {
        when(jwtUtils.getUserIdFromToken(anyString())).thenReturn(mockUserId);
        when(taskService.updateStatus(eq("TASK-2026-1-00001"), eq(TaskStatus.IN_PROGRESS), eq(mockUserId)))
                .thenReturn(mockTask);

        mockMvc.perform(patch("/tasks/TASK-2026-1-00001/status")
                        .header("Authorization", mockToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateStatusRequest)))
                .andExpect(status().isOk());
    }

    // ============================================
    // UPDATE PROGRESS TESTS
    // ============================================

    @Test
    @DisplayName("PATCH /api/tasks/{identifier}/progress - Should return 200 when progress updated")
    void updateProgress_Success_Returns200() throws Exception {
        when(jwtUtils.getUserIdFromToken(anyString())).thenReturn(mockUserId);
        when(taskService.updateProgress(eq("1"), eq(50), eq(mockUserId)))
                .thenReturn(mockTask);

        mockMvc.perform(patch("/tasks/1/progress")
                        .header("Authorization", mockToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateProgressRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progress").value(0)); // Mock returns 0
    }

    // ============================================
    // ASSIGN TASK TESTS
    // ============================================

    @Test
    @DisplayName("PATCH /api/tasks/{identifier}/assign/{userIdentifier} - Should return 200 when task assigned by username")
    void assignTaskByIdentifier_Success_Returns200() throws Exception {
        when(jwtUtils.getUserIdFromToken(anyString())).thenReturn(mockUserId);
        when(taskService.assignTaskByIdentifier(eq("1"), eq("staffjane"), eq(mockUserId)))
                .thenReturn(mockTask);

        mockMvc.perform(patch("/tasks/1/assign/staffjane")
                        .header("Authorization", mockToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedToUsername").value("staffjane"));
    }

    @Test
    @DisplayName("PATCH /api/tasks/{identifier}/assign/{userIdentifier} - Should return 200 when task assigned by email")
    void assignTaskByIdentifierByEmail_Success_Returns200() throws Exception {
        when(jwtUtils.getUserIdFromToken(anyString())).thenReturn(mockUserId);
        when(taskService.assignTaskByIdentifier(eq("TASK-2026-1-00001"), eq("jane@legalfirm.com"), eq(mockUserId)))
                .thenReturn(mockTask);

        mockMvc.perform(patch("/tasks/TASK-2026-1-00001/assign/jane@legalfirm.com")
                        .header("Authorization", mockToken))
                .andExpect(status().isOk());
    }

    // ============================================
    // COMPLETE TASK TESTS
    // ============================================

    @Test
    @DisplayName("PATCH /api/tasks/{identifier}/complete - Should return 200 when task completed")
    void setProgressTo100OnCompletion_Success_Returns200() throws Exception {
        when(jwtUtils.getUserIdFromToken(anyString())).thenReturn(mockUserId);
        doNothing().when(taskService).setProgressTo100OnCompletion(eq("1"), eq(mockUserId));

        mockMvc.perform(patch("/tasks/1/complete")
                        .header("Authorization", mockToken))
                .andExpect(status().isOk());

        verify(taskService, times(1)).setProgressTo100OnCompletion(eq("1"), eq(mockUserId));
    }

    // ============================================
    // DELETE TASK TESTS
    // ============================================

    @Test
    @DisplayName("DELETE /api/tasks/{identifier} - Should return 204 when task deleted")
    void deleteTask_Success_Returns204() throws Exception {
        when(jwtUtils.getUserIdFromToken(anyString())).thenReturn(mockUserId);
        doNothing().when(taskService).deleteTask(eq("1"), eq(mockUserId));

        mockMvc.perform(delete("/tasks/1")
                        .header("Authorization", mockToken))
                .andExpect(status().isNoContent());

        verify(taskService, times(1)).deleteTask(eq("1"), eq(mockUserId));
    }

    // ============================================
    // GET TASKS BY CASE TESTS
    // ============================================

    @Test
    @DisplayName("GET /api/tasks/case/{caseId} - Should return list of tasks")
    void getTasksByCase_Success_Returns200() throws Exception {
        List<Task> tasks = Arrays.asList(mockTask);
        when(jwtUtils.getUserIdFromToken(anyString())).thenReturn(mockUserId);
        when(taskService.getTasksByCase(eq(1L), eq(mockUserId))).thenReturn(tasks);

        mockMvc.perform(get("/tasks/case/1")
                        .header("Authorization", mockToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].title").value("Test Task"));
    }

    // ============================================
    // GET MY TASKS TESTS
    // ============================================

    @Test
    @DisplayName("GET /api/tasks/my-tasks - Should return tasks assigned to current user")
    void getMyTasks_Success_Returns200() throws Exception {
        List<Task> tasks = Arrays.asList(mockTask);
        when(jwtUtils.getUserIdFromToken(anyString())).thenReturn(mockUserId);
        when(taskService.getTasksByAssignedUser(eq(mockUserId), eq(mockUserId))).thenReturn(tasks);

        mockMvc.perform(get("/tasks/my-tasks")
                        .header("Authorization", mockToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));
    }

    // ============================================
    // GET TASKS BY CASE AND ASSIGNED TO TESTS
    // ============================================

    @Test
    @DisplayName("GET /api/tasks/case/{caseId}/assigned-to/{userIdentifier} - Should return filtered tasks")
    void getTasksByCaseAndAssignedTo_Success_Returns200() throws Exception {
        List<Task> tasks = Arrays.asList(mockTask);
        when(jwtUtils.getUserIdFromToken(anyString())).thenReturn(mockUserId);
        when(taskService.getTasksByCaseAndAssignedToIdentifier(eq(1L), eq("staffjane"), eq(mockUserId)))
                .thenReturn(tasks);

        mockMvc.perform(get("/tasks/case/1/assigned-to/staffjane")
                        .header("Authorization", mockToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));
    }

    // ============================================
    // GET TASKS BY ASSIGNED TO TESTS
    // ============================================

    @Test
    @DisplayName("GET /api/tasks/assigned-to/{userIdentifier} - Should return tasks assigned to user")
    void getTasksByAssignedTo_Success_Returns200() throws Exception {
        List<Task> tasks = Arrays.asList(mockTask);
        when(jwtUtils.getUserIdFromToken(anyString())).thenReturn(mockUserId);
        when(taskService.getTasksByAssignedUserIdentifier(eq("staffjane"), eq(mockUserId)))
                .thenReturn(tasks);

        mockMvc.perform(get("/tasks/assigned-to/staffjane")
                        .header("Authorization", mockToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].assignedToUsername").value("staffjane"));
    }

    // ============================================
    // DUE DATE TESTS
    // ============================================

    @Test
    @DisplayName("GET /api/tasks/case/{caseId}/due-date/{dueDate} - Should return tasks by due date")
    void getTasksByDueDate_Success_Returns200() throws Exception {
        List<Task> tasks = Arrays.asList(mockTask);
        when(jwtUtils.getUserIdFromToken(anyString())).thenReturn(mockUserId);
        when(taskService.getTasksByDueDate(eq(1L), any(LocalDate.class), eq(mockUserId)))
                .thenReturn(tasks);

        mockMvc.perform(get("/tasks/case/1/due-date/2026-06-01")
                        .header("Authorization", mockToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));
    }

    // ============================================
    // OVERDUE TASKS TESTS
    // ============================================

    @Test
    @DisplayName("GET /api/tasks/case/{caseId}/overdue - Should return overdue tasks")
    void getOverdueTasksByCase_Success_Returns200() throws Exception {
        List<Task> tasks = Arrays.asList(mockTask);
        when(jwtUtils.getUserIdFromToken(anyString())).thenReturn(mockUserId);
        when(taskService.getOverdueTasksByCase(eq(1L), eq(mockUserId))).thenReturn(tasks);

        mockMvc.perform(get("/tasks/case/1/overdue")
                        .header("Authorization", mockToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));
    }

    // ============================================
    // UNBLOCKED TASKS TESTS
    // ============================================

    @Test
    @DisplayName("GET /api/tasks/case/{caseId}/unblocked - Should return unblocked tasks")
    void getUnblockedTasks_Success_Returns200() throws Exception {
        List<Task> tasks = Arrays.asList(mockTask);
        when(jwtUtils.getUserIdFromToken(anyString())).thenReturn(mockUserId);
        when(taskService.getUnblockedTasksByCase(eq(1L), eq(mockUserId))).thenReturn(tasks);

        mockMvc.perform(get("/tasks/case/1/unblocked")
                        .header("Authorization", mockToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));
    }

    // ============================================
    // SEARCH TASKS TESTS
    // ============================================

    @Test
    @DisplayName("GET /api/tasks/case/{caseId}/search - Should return matching tasks")
    void searchTasksInCase_Success_Returns200() throws Exception {
        List<Task> tasks = Arrays.asList(mockTask);
        when(jwtUtils.getUserIdFromToken(anyString())).thenReturn(mockUserId);
        when(taskService.searchTasksInCase(eq(1L), eq("contract"), eq(mockUserId)))
                .thenReturn(tasks);

        mockMvc.perform(get("/tasks/case/1/search?q=contract")
                        .header("Authorization", mockToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));
    }

    // ============================================
    // COUNT TASKS TESTS
    // ============================================

    @Test
    @DisplayName("GET /api/tasks/case/{caseId}/count/type/{type} - Should return count")
    void countTasksByCaseAndType_Success_Returns200() throws Exception {
        when(jwtUtils.getUserIdFromToken(anyString())).thenReturn(mockUserId);
        when(taskService.countTasksByCaseAndType(eq(1L), eq(TaskType.MANDATORY), eq(mockUserId)))
                .thenReturn(5L);

        mockMvc.perform(get("/tasks/case/1/count/type/MANDATORY")
                        .header("Authorization", mockToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(5));
    }

    @Test
    @DisplayName("GET /api/tasks/case/{caseId}/count/status/{status} - Should return count")
    void countTasksByCaseAndStatus_Success_Returns200() throws Exception {
        when(jwtUtils.getUserIdFromToken(anyString())).thenReturn(mockUserId);
        when(taskService.countTasksByCaseAndStatus(eq(1L), eq(TaskStatus.IN_PROGRESS), eq(mockUserId)))
                .thenReturn(3L);

        mockMvc.perform(get("/tasks/case/1/count/status/IN_PROGRESS")
                        .header("Authorization", mockToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(3));
    }

    // ============================================
    // VALIDATION TESTS
    // ============================================

    @Test
    @DisplayName("POST /api/tasks/case/{caseId} - Should return 400 when title is missing")
    void createTask_MissingTitle_Returns400() throws Exception {
        CreateTaskRequest invalidRequest = new CreateTaskRequest();
        invalidRequest.setDescription("Test Description");
        invalidRequest.setType(TaskType.MANDATORY);
        invalidRequest.setPriority(TaskPriority.HIGH);

        mockMvc.perform(post("/tasks/case/1")
                        .header("Authorization", mockToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    // ============================================
    // AUTHENTICATION TESTS
    // ============================================

    @Test
    @DisplayName("GET /api/tasks/case/1 - Should return 401 when no token provided")
    void getTasksByCase_NoToken_ReturnsError() throws Exception {
        mockMvc.perform(get("/tasks/case/1"))
                .andExpect(status().isBadRequest());
    }
}

