package com.legalcase.controller;

import com.legalcase.dto.request.CreateTaskRequest;
import com.legalcase.dto.request.UpdateTaskProgressRequest;
import com.legalcase.dto.request.UpdateTaskStatusRequest;
import com.legalcase.dto.response.TaskResponse;
import com.legalcase.entity.Task;
import com.legalcase.enums.TaskPriority;
import com.legalcase.enums.TaskStatus;
import com.legalcase.enums.TaskType;
import com.legalcase.security.JwtUtils;
import com.legalcase.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/tasks")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Task Management", description = "Task creation, assignment, progress tracking, filtering, and workflow")
@SecurityRequirement(name = "Bearer Authentication")
public class TaskController {

    private final TaskService taskService;
    private final JwtUtils jwtUtils;

    // ============================================
    // CORE CRUD OPERATIONS
    // ============================================

    @Operation(
            summary = "Create a task in a case",
            description = "Creates a new task within a specific case. Supports task types (MANDATORY/OPTIONAL/REVIEW) and dependencies."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Task created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Not a case member"),
            @ApiResponse(responseCode = "422", description = "Invalid status or dependency")
    })
    @PostMapping("/case/{caseId}")
    public ResponseEntity<TaskResponse> createTaskInCase(
            @Parameter(description = "Case ID") @PathVariable Long caseId,
            @Valid @RequestBody CreateTaskRequest request,
            HttpServletRequest httpRequest) {

        Long userId = extractUserId(httpRequest);

        LocalDateTime dueDateTime = request.getDueDate() != null
                ? request.getDueDate().atStartOfDay()
                : null;

        Task task = taskService.createTask(
                request.getTitle(),
                request.getDescription(),
                request.getType(),
                request.getPriority(),
                dueDateTime,
                caseId,
                userId,
                request.getAssignedToUserId(),
                request.getDependsOnTaskId()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(TaskResponse.fromEntity(task));
    }

    @Operation(
            summary = "Get tasks by case",
            description = "Returns all tasks belonging to a specific case."
    )
    @GetMapping("/case/{caseId}")
    public ResponseEntity<List<TaskResponse>> getTasksByCase(@Parameter(description = "Case ID") @PathVariable Long caseId) {
        List<Task> tasks = taskService.getTasksByCase(caseId);
        return ResponseEntity.ok(tasks.stream()
                .map(TaskResponse::fromEntity)
                .collect(Collectors.toList()));
    }

    @Operation(
            summary = "Get task by ID",
            description = "Retrieves a single task by its unique ID."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Task found"),
            @ApiResponse(responseCode = "404", description = "Task not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<TaskResponse> getTaskById(@Parameter(description = "Task ID") @PathVariable Long id) {
        Task task = taskService.findById(id);
        return ResponseEntity.ok(TaskResponse.fromEntity(task));
    }

    // ============================================
    // BATCH UPDATE ENDPOINTS
    // ============================================

    @Operation(
            summary = "Update task status",
            description = "Updates task status (TODO → IN_PROGRESS → REVIEW → COMPLETED). Validates workflow and permissions."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Status updated"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "422", description = "Invalid status transition")
    })
    @PatchMapping("/{id}/status")
    public ResponseEntity<TaskResponse> updateStatus(
            @Parameter(description = "Task ID") @PathVariable Long id,
            @Valid @RequestBody UpdateTaskStatusRequest request,
            HttpServletRequest httpRequest) {

        Long userId = extractUserId(httpRequest);

        Task task = taskService.updateStatus(id, request.getStatus(), userId);
        return ResponseEntity.ok(TaskResponse.fromEntity(task));
    }

    @Operation(
            summary = "Update task progress",
            description = "Updates task progress percentage (0-100). Auto-updates status based on progress."
    )
    @PatchMapping("/{id}/progress")
    public ResponseEntity<TaskResponse> updateProgress(
            @Parameter(description = "Task ID") @PathVariable Long id,
            @Valid @RequestBody UpdateTaskProgressRequest request,
            HttpServletRequest httpRequest) {

        Long userId = extractUserId(httpRequest);

        Task task = taskService.updateProgress(id, request.getProgress(), userId);
        return ResponseEntity.ok(TaskResponse.fromEntity(task));
    }


    @Operation(
            summary = "Set task progress to 100% on completion",
            description = "Force-sets a task's progress to 100% when it is marked as COMPLETED. Useful for batch operations or fixing inconsistent states."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Progress updated successfully"),
            @ApiResponse(responseCode = "404", description = "Task not found"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PatchMapping("/{id}/complete")
    public ResponseEntity<Void> setProgressTo100OnCompletion(
            @Parameter(description = "Task ID") @PathVariable Long id,
            HttpServletRequest httpRequest) {

        Long userId = extractUserId(httpRequest);
        log.info("User {} setting task {} progress to 100%% on completion", userId, id);

        taskService.setProgressTo100OnCompletion(id);
        return ResponseEntity.ok().build();
    }

    @Operation(
            summary = "Assign task to user",
            description = "Assigns a task to a specific user. User must be a member of the case."
    )
    @PatchMapping("/{id}/assign")
    public ResponseEntity<TaskResponse> assignTask(
            @Parameter(description = "Task ID") @PathVariable Long id,
            @Parameter(description = "User ID to assign to") @RequestParam Long assignedToUserId,
            HttpServletRequest httpRequest) {

        Long userId = extractUserId(httpRequest);

        Task task = taskService.assignTask(id, assignedToUserId, userId);
        return ResponseEntity.ok(TaskResponse.fromEntity(task));
    }

    // ============================================
    // FILTER ENDPOINTS
    // ============================================

    @Operation(
            summary = "Get all tasks by status",
            description = "Returns all tasks with a specific status across all cases. Useful for admin dashboard."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Tasks retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/status/{status}")
    public ResponseEntity<List<TaskResponse>> getTasksByStatus(
            @Parameter(description = "Task Status (TODO, IN_PROGRESS, REVIEW, COMPLETED)")
            @PathVariable TaskStatus status,
            HttpServletRequest httpRequest) {

        extractUserId(httpRequest);
        List<Task> tasks = taskService.getTasksByStatus(status);
        return ResponseEntity.ok(tasks.stream()
                .map(TaskResponse::fromEntity)
                .collect(Collectors.toList()));
    }

    @Operation(
            summary = "Get all tasks assigned to a user",
            description = "Returns all tasks assigned to a specific user across all cases."
    )
    @GetMapping("/assigned-to/{userId}")
    public ResponseEntity<List<TaskResponse>> getTasksByAssignedTo(
            @Parameter(description = "User ID") @PathVariable Long userId,
            HttpServletRequest httpRequest) {

        extractUserId(httpRequest);
        List<Task> tasks = taskService.getTasksByAssignedTo(userId);
        return ResponseEntity.ok(tasks.stream()
                .map(TaskResponse::fromEntity)
                .collect(Collectors.toList()));
    }

    @Operation(
            summary = "Get all tasks for a case",
            description = "Returns all tasks belonging to a specific case. (Alternative to /case/{caseId})"
    )
    @GetMapping("/legal-case/{caseId}")
    public ResponseEntity<List<TaskResponse>> getTasksByLegalCase(
            @Parameter(description = "Case ID") @PathVariable Long caseId,
            HttpServletRequest httpRequest) {

        extractUserId(httpRequest);
        List<Task> tasks = taskService.getTasksByCase(caseId);
        return ResponseEntity.ok(tasks.stream()
                .map(TaskResponse::fromEntity)
                .collect(Collectors.toList()));
    }

    @Operation(
            summary = "Get tasks by case and status",
            description = "Returns tasks filtered by case ID and status (TODO, IN_PROGRESS, REVIEW, COMPLETED)."
    )
    @GetMapping("/case/{caseId}/status/{status}")
    public ResponseEntity<List<TaskResponse>> getTasksByCaseAndStatus(
            @Parameter(description = "Case ID") @PathVariable Long caseId,
            @Parameter(description = "Task Status") @PathVariable TaskStatus status) {
        List<Task> tasks = taskService.getTasksByCaseAndStatus(caseId, status);
        return ResponseEntity.ok(tasks.stream()
                .map(TaskResponse::fromEntity)
                .collect(Collectors.toList()));
    }

    @Operation(
            summary = "Get tasks by case and assigned user",
            description = "Returns tasks filtered by case ID and assigned user ID."
    )
    @GetMapping("/case/{caseId}/assigned-to/{userId}")
    public ResponseEntity<List<TaskResponse>> getTasksByCaseAndAssignedTo(
            @Parameter(description = "Case ID") @PathVariable Long caseId,
            @Parameter(description = "User ID") @PathVariable Long userId) {
        List<Task> tasks = taskService.getTasksByCaseAndAssignedTo(caseId, userId);
        return ResponseEntity.ok(tasks.stream()
                .map(TaskResponse::fromEntity)
                .collect(Collectors.toList()));
    }

    @Operation(
            summary = "Get tasks by case and type",
            description = "Returns tasks filtered by case ID and type (MANDATORY, OPTIONAL, REVIEW)."
    )
    @GetMapping("/case/{caseId}/type/{type}")
    public ResponseEntity<List<TaskResponse>> getTasksByCaseAndType(
            @Parameter(description = "Case ID") @PathVariable Long caseId,
            @Parameter(description = "Task Type") @PathVariable TaskType type) {
        List<Task> tasks = taskService.getTasksByCaseAndType(caseId, type);
        return ResponseEntity.ok(tasks.stream()
                .map(TaskResponse::fromEntity)
                .collect(Collectors.toList()));
    }

    @Operation(
            summary = "Get tasks by priority",
            description = "Returns all tasks with a specific priority (LOW, MEDIUM, HIGH, URGENT)."
    )
    @GetMapping("/priority/{priority}")
    public ResponseEntity<List<TaskResponse>> getTasksByPriority(
            @Parameter(description = "Task Priority") @PathVariable TaskPriority priority) {
        List<Task> tasks = taskService.getTasksByPriority(priority);
        return ResponseEntity.ok(tasks.stream()
                .map(TaskResponse::fromEntity)
                .collect(Collectors.toList()));
    }

    @Operation(
            summary = "Get tasks by type",
            description = "Returns all tasks with a specific type (MANDATORY, OPTIONAL, REVIEW)."
    )
    @GetMapping("/type/{type}")
    public ResponseEntity<List<TaskResponse>> getTasksByType(
            @Parameter(description = "Task Type") @PathVariable TaskType type) {
        List<Task> tasks = taskService.getTasksByType(type);
        return ResponseEntity.ok(tasks.stream()
                .map(TaskResponse::fromEntity)
                .collect(Collectors.toList()));
    }

    // ============================================
    // COUNT ENDPOINTS
    // ============================================

    @Operation(
            summary = "Count tasks by case and type",
            description = "Returns the count of tasks for a case filtered by type."
    )
    @GetMapping("/case/{caseId}/count/type/{type}")
    public ResponseEntity<Map<String, Long>> countTasksByCaseAndType(
            @Parameter(description = "Case ID") @PathVariable Long caseId,
            @Parameter(description = "Task Type") @PathVariable TaskType type) {
        long count = taskService.countTasksByCaseAndType(caseId, type);
        return ResponseEntity.ok(Map.of("count", count));
    }

    @Operation(
            summary = "Count tasks by case, type, and status",
            description = "Returns the count of tasks for a case filtered by type and status."
    )
    @GetMapping("/case/{caseId}/count/type/{type}/status/{status}")
    public ResponseEntity<Map<String, Long>> countTasksByCaseAndTypeAndStatus(
            @Parameter(description = "Case ID") @PathVariable Long caseId,
            @Parameter(description = "Task Type") @PathVariable TaskType type,
            @Parameter(description = "Task Status") @PathVariable TaskStatus status) {
        long count = taskService.countTasksByCaseAndTypeAndStatus(caseId, type, status);
        return ResponseEntity.ok(Map.of("count", count));
    }

    @Operation(
            summary = "Count tasks by case and status",
            description = "Returns the count of tasks for a case filtered by status."
    )
    @GetMapping("/case/{caseId}/count/status/{status}")
    public ResponseEntity<Map<String, Long>> countTasksByCaseAndStatus(
            @Parameter(description = "Case ID") @PathVariable Long caseId,
            @Parameter(description = "Task Status") @PathVariable TaskStatus status) {
        long count = taskService.countTasksByCaseAndStatus(caseId, status);
        return ResponseEntity.ok(Map.of("count", count));
    }

    // ============================================
    // OVERDUE & UNBLOCKED ENDPOINTS
    // ============================================

    @Operation(
            summary = "Get my assigned tasks",
            description = "Returns all tasks assigned to the current authenticated user."
    )
    @GetMapping("/my-tasks")
    public ResponseEntity<List<TaskResponse>> getMyTasks(HttpServletRequest httpRequest) {
        Long userId = extractUserId(httpRequest);

        List<Task> tasks = taskService.getTasksByAssignedUser(userId);
        return ResponseEntity.ok(tasks.stream()
                .map(TaskResponse::fromEntity)
                .collect(Collectors.toList()));
    }

    @Operation(
            summary = "Get overdue tasks by case",
            description = "Returns all overdue tasks for a specific case."
    )
    @GetMapping("/case/{caseId}/overdue")
    public ResponseEntity<List<TaskResponse>> getOverdueTasksByCase(
            @Parameter(description = "Case ID") @PathVariable Long caseId) {
        List<Task> tasks = taskService.getOverdueTasksByCase(caseId);
        return ResponseEntity.ok(tasks.stream()
                .map(TaskResponse::fromEntity)
                .collect(Collectors.toList()));
    }

    @Operation(
            summary = "Get all overdue tasks",
            description = "Returns all overdue tasks across all cases."
    )
    @GetMapping("/overdue")
    public ResponseEntity<List<TaskResponse>> getAllOverdueTasks() {
        List<Task> tasks = taskService.getAllOverdueTasks();
        return ResponseEntity.ok(tasks.stream()
                .map(TaskResponse::fromEntity)
                .collect(Collectors.toList()));
    }

    @Operation(
            summary = "Get unblocked tasks",
            description = "Returns tasks that are ready to work on (dependencies completed)."
    )
    @GetMapping("/case/{caseId}/unblocked")
    public ResponseEntity<List<TaskResponse>> getUnblockedTasks(
            @Parameter(description = "Case ID") @PathVariable Long caseId) {
        List<Task> tasks = taskService.getUnblockedTasksByCase(caseId);
        return ResponseEntity.ok(tasks.stream()
                .map(TaskResponse::fromEntity)
                .collect(Collectors.toList()));
    }

    // ============================================
    // DUE DATE ENDPOINTS
    // ============================================

    @Operation(
            summary = "Get tasks by due date",
            description = "Returns all tasks with a specific due date (exact match)."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Tasks retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/due-date/{dueDate}")
    public ResponseEntity<List<TaskResponse>> getTasksByDueDate(
            @Parameter(description = "Due date (YYYY-MM-DD)") @PathVariable LocalDate dueDate,
            HttpServletRequest httpRequest) {

        extractUserId(httpRequest);
        List<Task> tasks = taskService.getTasksByDueDate(dueDate);
        return ResponseEntity.ok(tasks.stream()
                .map(TaskResponse::fromEntity)
                .collect(Collectors.toList()));
    }

    @Operation(
            summary = "Get tasks due before date",
            description = "Returns all tasks with due date before the specified date (overdue tasks)."
    )
    @GetMapping("/due-date/before/{date}")
    public ResponseEntity<List<TaskResponse>> getTasksByDueDateBefore(
            @Parameter(description = "Date (YYYY-MM-DD)") @PathVariable LocalDate date,
            HttpServletRequest httpRequest) {

        extractUserId(httpRequest);
        List<Task> tasks = taskService.getTasksByDueDateBefore(date);
        return ResponseEntity.ok(tasks.stream()
                .map(TaskResponse::fromEntity)
                .collect(Collectors.toList()));
    }

    @Operation(
            summary = "Get tasks due after date",
            description = "Returns all tasks with due date after the specified date (upcoming tasks)."
    )
    @GetMapping("/due-date/after/{date}")
    public ResponseEntity<List<TaskResponse>> getTasksByDueDateAfter(
            @Parameter(description = "Date (YYYY-MM-DD)") @PathVariable LocalDate date,
            HttpServletRequest httpRequest) {

        extractUserId(httpRequest);
        List<Task> tasks = taskService.getTasksByDueDateAfter(date);
        return ResponseEntity.ok(tasks.stream()
                .map(TaskResponse::fromEntity)
                .collect(Collectors.toList()));
    }

    @Operation(
            summary = "Get tasks due between dates",
            description = "Returns all tasks with due date between start and end dates."
    )
    @GetMapping("/due-date/between")
    public ResponseEntity<List<TaskResponse>> getTasksByDueDateBetween(
            @Parameter(description = "Start date (YYYY-MM-DD)") @RequestParam LocalDate start,
            @Parameter(description = "End date (YYYY-MM-DD)") @RequestParam LocalDate end,
            HttpServletRequest httpRequest) {

        extractUserId(httpRequest);
        List<Task> tasks = taskService.getTasksByDueDateBetween(start, end);
        return ResponseEntity.ok(tasks.stream()
                .map(TaskResponse::fromEntity)
                .collect(Collectors.toList()));
    }

    // ============================================
    // HELPER METHODS
    // ============================================

    private Long extractUserId(HttpServletRequest request) {
        String token = extractToken(request);
        return jwtUtils.getUserIdFromToken(token);
    }

    private String extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new RuntimeException("Missing or invalid Authorization header");
        }
        return authHeader.substring(7);
    }
}

