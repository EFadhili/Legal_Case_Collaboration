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

    @PostMapping("/case/{caseId}")
    public ResponseEntity<TaskResponse> createTaskInCase(
            @PathVariable Long caseId,
            @Valid @RequestBody CreateTaskRequest request,
            HttpServletRequest httpRequest) {

        Long userId = extractUserId(httpRequest);
        LocalDateTime dueDateTime = request.getDueDate() != null
                ? request.getDueDate().atStartOfDay()
                : null;

        Task task = taskService.createTask(
                request.getTitle(), request.getDescription(), request.getType(),
                request.getPriority(), dueDateTime, caseId, userId,
                request.getAssignedToUserId(), request.getDependsOnTaskId());

        return ResponseEntity.status(HttpStatus.CREATED).body(TaskResponse.fromEntity(task));
    }

    @GetMapping("/{identifier}")
    public ResponseEntity<TaskResponse> getTask(
            @PathVariable String identifier,
            HttpServletRequest httpRequest) {

        Long userId = extractUserId(httpRequest);
        Task task = taskService.getTask(identifier, userId);
        return ResponseEntity.ok(TaskResponse.fromEntity(task));
    }

    @PatchMapping("/{identifier}/status")
    public ResponseEntity<TaskResponse> updateStatus(
            @PathVariable String identifier,
            @Valid @RequestBody UpdateTaskStatusRequest request,
            HttpServletRequest httpRequest) {

        Long userId = extractUserId(httpRequest);
        Task task = taskService.updateStatus(identifier, request.getStatus(), userId);
        return ResponseEntity.ok(TaskResponse.fromEntity(task));
    }

    @PatchMapping("/{identifier}/progress")
    public ResponseEntity<TaskResponse> updateProgress(
            @PathVariable String identifier,
            @Valid @RequestBody UpdateTaskProgressRequest request,
            HttpServletRequest httpRequest) {

        Long userId = extractUserId(httpRequest);
        Task task = taskService.updateProgress(identifier, request.getProgress(), userId);
        return ResponseEntity.ok(TaskResponse.fromEntity(task));
    }

    @PatchMapping("/{identifier}/assign/{userIdentifier}")
    public ResponseEntity<TaskResponse> assignTaskByIdentifier(
            @PathVariable String identifier,
            @PathVariable String userIdentifier,
            HttpServletRequest httpRequest) {

        Long userId = extractUserId(httpRequest);
        Task task = taskService.assignTaskByIdentifier(identifier, userIdentifier, userId);
        return ResponseEntity.ok(TaskResponse.fromEntity(task));
    }

    @PatchMapping("/{identifier}/complete")
    public ResponseEntity<Void> setProgressTo100OnCompletion(
            @PathVariable String identifier,
            HttpServletRequest httpRequest) {

        Long userId = extractUserId(httpRequest);
        taskService.setProgressTo100OnCompletion(identifier, userId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{identifier}")
    public ResponseEntity<Void> deleteTask(
            @PathVariable String identifier,
            HttpServletRequest httpRequest) {

        Long userId = extractUserId(httpRequest);
        taskService.deleteTask(identifier, userId);
        return ResponseEntity.noContent().build();
    }

    // ============================================
    // LIST ENDPOINTS
    // ============================================

    @GetMapping("/case/{caseId}")
    public ResponseEntity<List<TaskResponse>> getTasksByCase(
            @PathVariable Long caseId,
            HttpServletRequest httpRequest) {

        Long userId = extractUserId(httpRequest);
        List<Task> tasks = taskService.getTasksByCase(caseId, userId);
        return ResponseEntity.ok(tasks.stream()
                .map(TaskResponse::fromEntity)
                .collect(Collectors.toList()));
    }

    @GetMapping("/my-tasks")
    public ResponseEntity<List<TaskResponse>> getMyTasks(HttpServletRequest httpRequest) {
        Long userId = extractUserId(httpRequest);
        List<Task> tasks = taskService.getTasksByAssignedUser(userId, userId);
        return ResponseEntity.ok(tasks.stream()
                .map(TaskResponse::fromEntity)
                .collect(Collectors.toList()));
    }

    // ============================================
    // FILTER ENDPOINTS
    // ============================================

    @GetMapping("/case/{caseId}/status/{status}")
    public ResponseEntity<List<TaskResponse>> getTasksByCaseAndStatus(
            @PathVariable Long caseId,
            @PathVariable TaskStatus status,
            HttpServletRequest httpRequest) {

        Long userId = extractUserId(httpRequest);
        List<Task> tasks = taskService.getTasksByCaseAndStatus(caseId, status, userId);
        return ResponseEntity.ok(tasks.stream()
                .map(TaskResponse::fromEntity)
                .collect(Collectors.toList()));
    }

    @GetMapping("/case/{caseId}/type/{type}")
    public ResponseEntity<List<TaskResponse>> getTasksByCaseAndType(
            @PathVariable Long caseId,
            @PathVariable TaskType type,
            HttpServletRequest httpRequest) {

        Long userId = extractUserId(httpRequest);
        List<Task> tasks = taskService.getTasksByCaseAndType(caseId, type, userId);
        return ResponseEntity.ok(tasks.stream()
                .map(TaskResponse::fromEntity)
                .collect(Collectors.toList()));
    }

    @GetMapping("/case/{caseId}/assigned-to/{userIdentifier}")
    public ResponseEntity<List<TaskResponse>> getTasksByCaseAndAssignedTo(
            @PathVariable Long caseId,
            @PathVariable String userIdentifier,
            HttpServletRequest httpRequest) {

        Long userId = extractUserId(httpRequest);
        List<Task> tasks = taskService.getTasksByCaseAndAssignedToIdentifier(caseId, userIdentifier, userId);
        return ResponseEntity.ok(tasks.stream()
                .map(TaskResponse::fromEntity)
                .collect(Collectors.toList()));
    }

    @GetMapping("/assigned-to/{userIdentifier}")
    public ResponseEntity<List<TaskResponse>> getTasksByAssignedTo(
            @PathVariable String userIdentifier,
            HttpServletRequest httpRequest) {

        Long userId = extractUserId(httpRequest);
        List<Task> tasks = taskService.getTasksByAssignedUserIdentifier(userIdentifier, userId);
        return ResponseEntity.ok(tasks.stream()
                .map(TaskResponse::fromEntity)
                .collect(Collectors.toList()));
    }

    // ============================================
// DUE DATE ENDPOINTS (Now require caseId)
// ============================================

    @GetMapping("/case/{caseId}/due-date/{dueDate}")
    public ResponseEntity<List<TaskResponse>> getTasksByDueDate(
            @PathVariable Long caseId,
            @PathVariable LocalDate dueDate,
            HttpServletRequest httpRequest) {

        Long userId = extractUserId(httpRequest);
        List<Task> tasks = taskService.getTasksByDueDate(caseId, dueDate, userId);
        return ResponseEntity.ok(tasks.stream()
                .map(TaskResponse::fromEntity)
                .collect(Collectors.toList()));
    }

    @GetMapping("/case/{caseId}/due-date/before/{date}")
    public ResponseEntity<List<TaskResponse>> getTasksByDueDateBefore(
            @PathVariable Long caseId,
            @PathVariable LocalDate date,
            HttpServletRequest httpRequest) {

        Long userId = extractUserId(httpRequest);
        List<Task> tasks = taskService.getTasksByDueDateBefore(caseId, date, userId);
        return ResponseEntity.ok(tasks.stream()
                .map(TaskResponse::fromEntity)
                .collect(Collectors.toList()));
    }

    @GetMapping("/case/{caseId}/due-date/after/{date}")
    public ResponseEntity<List<TaskResponse>> getTasksByDueDateAfter(
            @PathVariable Long caseId,
            @PathVariable LocalDate date,
            HttpServletRequest httpRequest) {

        Long userId = extractUserId(httpRequest);
        List<Task> tasks = taskService.getTasksByDueDateAfter(caseId, date, userId);
        return ResponseEntity.ok(tasks.stream()
                .map(TaskResponse::fromEntity)
                .collect(Collectors.toList()));
    }

    @GetMapping("/case/{caseId}/due-date/between")
    public ResponseEntity<List<TaskResponse>> getTasksByDueDateBetween(
            @PathVariable Long caseId,
            @RequestParam LocalDate start,
            @RequestParam LocalDate end,
            HttpServletRequest httpRequest) {

        Long userId = extractUserId(httpRequest);
        List<Task> tasks = taskService.getTasksByDueDateBetween(caseId, start, end, userId);
        return ResponseEntity.ok(tasks.stream()
                .map(TaskResponse::fromEntity)
                .collect(Collectors.toList()));
    }

    // ============================================
    // SEARCH
    // ============================================

    @GetMapping("/case/{caseId}/search")
    public ResponseEntity<List<TaskResponse>> searchTasksInCase(
            @PathVariable Long caseId,
            @RequestParam String q,
            HttpServletRequest httpRequest) {

        Long userId = extractUserId(httpRequest);
        List<Task> tasks = taskService.searchTasksInCase(caseId, q, userId);
        return ResponseEntity.ok(tasks.stream()
                .map(TaskResponse::fromEntity)
                .collect(Collectors.toList()));
    }

    // ============================================
    // COUNT ENDPOINTS
    // ============================================

    @GetMapping("/case/{caseId}/count/type/{type}")
    public ResponseEntity<Map<String, Long>> countTasksByCaseAndType(
            @PathVariable Long caseId,
            @PathVariable TaskType type,
            HttpServletRequest httpRequest) {

        Long userId = extractUserId(httpRequest);
        long count = taskService.countTasksByCaseAndType(caseId, type, userId);
        return ResponseEntity.ok(Map.of("count", count));
    }

    @GetMapping("/case/{caseId}/count/type/{type}/status/{status}")
    public ResponseEntity<Map<String, Long>> countTasksByCaseAndTypeAndStatus(
            @PathVariable Long caseId,
            @PathVariable TaskType type,
            @PathVariable TaskStatus status,
            HttpServletRequest httpRequest) {

        Long userId = extractUserId(httpRequest);
        long count = taskService.countTasksByCaseAndTypeAndStatus(caseId, type, status, userId);
        return ResponseEntity.ok(Map.of("count", count));
    }

    @GetMapping("/case/{caseId}/count/status/{status}")
    public ResponseEntity<Map<String, Long>> countTasksByCaseAndStatus(
            @PathVariable Long caseId,
            @PathVariable TaskStatus status,
            HttpServletRequest httpRequest) {

        Long userId = extractUserId(httpRequest);
        long count = taskService.countTasksByCaseAndStatus(caseId, status, userId);
        return ResponseEntity.ok(Map.of("count", count));
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