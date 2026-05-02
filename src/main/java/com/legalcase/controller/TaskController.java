package com.legalcase.controller;

import com.legalcase.dto.request.ApproveTaskRequest;
import com.legalcase.dto.request.CreateTaskRequest;
import com.legalcase.dto.request.UpdateTaskProgressRequest;
import com.legalcase.dto.request.UpdateTaskStatusRequest;
import com.legalcase.dto.response.TaskResponse;
import com.legalcase.entity.Task;
import com.legalcase.security.JwtUtils;
import com.legalcase.service.TaskService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/tasks")
@RequiredArgsConstructor
@Slf4j
public class TaskController {

    private final TaskService taskService;
    private final JwtUtils jwtUtils;

    @PostMapping
    public ResponseEntity<TaskResponse> createTask(
            @Valid @RequestBody CreateTaskRequest request,
            HttpServletRequest httpRequest) {

        Long userId = extractUserId(httpRequest);

        // IMPORTANT: The request doesn't contain caseId, so you need to add it to the request DTO
        // Or use the case/{caseId} endpoint instead
        throw new RuntimeException("Please use POST /tasks/case/{caseId} to create tasks");
    }

    @PostMapping("/case/{caseId}")
    public ResponseEntity<TaskResponse> createTaskInCase(
            @PathVariable Long caseId,
            @Valid @RequestBody CreateTaskRequest request,
            HttpServletRequest httpRequest) {

        Long userId = extractUserId(httpRequest);

        // Convert LocalDate to LocalDateTime if needed
        LocalDateTime dueDateTime = request.getDueDate() != null
                ? request.getDueDate().atStartOfDay()
                : null;

        Task task = taskService.createTask(
                request.getTitle(),           // String title
                request.getDescription(),     // String description
                request.getType(),            // TaskType type
                request.getPriority(),        // TaskPriority priority
                dueDateTime,                  // LocalDateTime dueDate
                caseId,                       // Long caseId
                userId,                       // Long createdById
                request.getAssignedToUserId(), // Long assignedToUserId (can be null)
                request.getDependsOnTaskId()  // Long dependsOnTaskId (can be null)
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(TaskResponse.fromEntity(task));
    }

    @GetMapping("/case/{caseId}")
    public ResponseEntity<List<TaskResponse>> getTasksByCase(@PathVariable Long caseId) {
        List<Task> tasks = taskService.getTasksByCase(caseId);
        return ResponseEntity.ok(tasks.stream()
                .map(TaskResponse::fromEntity)
                .collect(Collectors.toList()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TaskResponse> getTaskById(@PathVariable Long id) {
        Task task = taskService.findById(id);
        return ResponseEntity.ok(TaskResponse.fromEntity(task));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<TaskResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTaskStatusRequest request,
            HttpServletRequest httpRequest) {

        Long userId = extractUserId(httpRequest);

        Task task = taskService.updateStatus(id, request.getStatus(), userId);
        return ResponseEntity.ok(TaskResponse.fromEntity(task));
    }

    @PatchMapping("/{id}/progress")
    public ResponseEntity<TaskResponse> updateProgress(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTaskProgressRequest request,
            HttpServletRequest httpRequest) {

        Long userId = extractUserId(httpRequest);

        Task task = taskService.updateProgress(id, request.getProgress(), userId);
        return ResponseEntity.ok(TaskResponse.fromEntity(task));
    }

    @PatchMapping("/{id}/assign")
    public ResponseEntity<TaskResponse> assignTask(
            @PathVariable Long id,
            @RequestParam Long assignedToUserId,
            HttpServletRequest httpRequest) {

        Long userId = extractUserId(httpRequest);

        Task task = taskService.assignTask(id, assignedToUserId, userId);
        return ResponseEntity.ok(TaskResponse.fromEntity(task));
    }

    @GetMapping("/my-tasks")
    public ResponseEntity<List<TaskResponse>> getMyTasks(HttpServletRequest httpRequest) {
        Long userId = extractUserId(httpRequest);

        List<Task> tasks = taskService.getTasksByAssignedUser(userId);
        return ResponseEntity.ok(tasks.stream()
                .map(TaskResponse::fromEntity)
                .collect(Collectors.toList()));
    }

    @GetMapping("/case/{caseId}/overdue")
    public ResponseEntity<List<TaskResponse>> getOverdueTasks(@PathVariable Long caseId) {
        List<Task> tasks = taskService.getOverdueTasksByCase(caseId);
        return ResponseEntity.ok(tasks.stream()
                .map(TaskResponse::fromEntity)
                .collect(Collectors.toList()));
    }

    @GetMapping("/case/{caseId}/unblocked")
    public ResponseEntity<List<TaskResponse>> getUnblockedTasks(@PathVariable Long caseId) {
        List<Task> tasks = taskService.getUnblockedTasksByCase(caseId);
        return ResponseEntity.ok(tasks.stream()
                .map(TaskResponse::fromEntity)
                .collect(Collectors.toList()));
    }

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