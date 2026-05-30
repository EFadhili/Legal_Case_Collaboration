package com.legalcase.controller;

import com.legalcase.dto.request.AIConversationRequest;
import com.legalcase.dto.request.AIQueryRequest;
import com.legalcase.dto.request.RateInteractionRequest;
import com.legalcase.dto.response.AIInteractionResponse;
import com.legalcase.dto.response.AIResponse;
import com.legalcase.security.JwtUtils;
import com.legalcase.service.AIService;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "AI Assistant", description = "Gemini-powered legal document analysis, Q&A, and summarization")
@SecurityRequirement(name = "Bearer Authentication")
public class AIController {

    private final AIService aiService;
    private final JwtUtils jwtUtils;

    // ============================================
    // QUERY ENDPOINTS
    // ============================================

    @Operation(summary = "Ask a question to the AI assistant")
    @PostMapping("/query")
    public ResponseEntity<AIResponse> query(
            @Valid @RequestBody AIQueryRequest request,
            HttpServletRequest httpRequest) {

        String userIdentifier = extractUserIdentifier(httpRequest);
        AIResponse response = aiService.processQuery(request, userIdentifier);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Multi-turn conversation with AI")
    @PostMapping("/conversation")
    public ResponseEntity<AIResponse> conversation(
            @Valid @RequestBody AIConversationRequest request,
            HttpServletRequest httpRequest) {

        String userIdentifier = extractUserIdentifier(httpRequest);
        AIResponse response = aiService.processConversation(request, userIdentifier);
        return ResponseEntity.ok(response);
    }

    // ============================================
    // GET HISTORY ENDPOINTS
    // ============================================

    @Operation(summary = "Get AI interaction history", description = "Returns the current user's previous AI queries")
    @GetMapping("/history")
    public ResponseEntity<Page<AIInteractionResponse>> getHistory(
            @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Number of items per page") @RequestParam(defaultValue = "20") int size,
            HttpServletRequest httpRequest) {

        String userIdentifier = extractUserIdentifier(httpRequest);
        Pageable pageable = PageRequest.of(page, size);
        Page<AIInteractionResponse> interactions = aiService.getUserHistory(userIdentifier, pageable);
        return ResponseEntity.ok(interactions);
    }

    @Operation(summary = "Get AI history for a specific case")
    @GetMapping("/case/{caseIdentifier}/history")
    public ResponseEntity<?> getCaseHistory(
            @Parameter(description = "Case ID or Case Number") @PathVariable String caseIdentifier,
            HttpServletRequest httpRequest) {

        String userIdentifier = extractUserIdentifier(httpRequest);
        var interactions = aiService.getCaseHistory(caseIdentifier, userIdentifier);
        return ResponseEntity.ok(interactions);
    }

    // ============================================
    // GET SINGLE INTERACTION
    // ============================================

    @Operation(summary = "Get AI interaction by ID or interaction number")
    @GetMapping("/{identifier}")
    public ResponseEntity<AIInteractionResponse> getInteraction(
            @Parameter(description = "Interaction ID or Number (e.g., AI-2026-00001)")
            @PathVariable String identifier,
            HttpServletRequest httpRequest) {

        String userIdentifier = extractUserIdentifier(httpRequest);
        var interaction = aiService.findByIdentifier(identifier, userIdentifier);
        return ResponseEntity.ok(AIInteractionResponse.fromEntity(interaction));
    }

    // ============================================
    // SEARCH ENDPOINTS
    // ============================================

    @Operation(summary = "Search my AI interactions")
    @GetMapping("/search")
    public ResponseEntity<Page<AIInteractionResponse>> searchMyHistory(
            @Parameter(description = "Search term") @RequestParam String q,
            @Parameter(description = "Page number") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
            HttpServletRequest httpRequest) {

        String userIdentifier = extractUserIdentifier(httpRequest);
        Pageable pageable = PageRequest.of(page, size);
        Page<AIInteractionResponse> results = aiService.searchUserHistory(userIdentifier, q, pageable);
        return ResponseEntity.ok(results);
    }

    @Operation(summary = "Search AI interactions in a case")
    @GetMapping("/case/{caseIdentifier}/search")
    public ResponseEntity<Page<AIInteractionResponse>> searchCaseHistory(
            @Parameter(description = "Case ID or Case Number") @PathVariable String caseIdentifier,
            @Parameter(description = "Search term") @RequestParam String q,
            @Parameter(description = "Page number") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
            HttpServletRequest httpRequest) {

        String userIdentifier = extractUserIdentifier(httpRequest);
        Pageable pageable = PageRequest.of(page, size);
        Page<AIInteractionResponse> results = aiService.searchCaseHistory(caseIdentifier, userIdentifier, q, pageable);
        return ResponseEntity.ok(results);
    }

    @Operation(summary = "Admin global search", description = "Search all AI interactions in the system. Admin only.")
    @GetMapping("/admin/search")
    public ResponseEntity<Page<AIInteractionResponse>> adminGlobalSearch(
            @Parameter(description = "Search term") @RequestParam String q,
            @Parameter(description = "Page number") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
            HttpServletRequest httpRequest) {

        String userIdentifier = extractUserIdentifier(httpRequest);
        Pageable pageable = PageRequest.of(page, size);
        Page<AIInteractionResponse> results = aiService.adminGlobalSearch(userIdentifier, q, pageable);
        return ResponseEntity.ok(results);
    }

    // ============================================
    // RATING ENDPOINT (PATCH)
    // ============================================

    @Operation(summary = "Rate an AI interaction", description = "Provides feedback on AI response quality. Rating from 1 to 5 stars.")
    @PatchMapping("/{identifier}/rating")
    public ResponseEntity<Void> rateInteraction(
            @Parameter(description = "Interaction ID or Number") @PathVariable String identifier,
            @Valid @RequestBody RateInteractionRequest request,
            HttpServletRequest httpRequest) {

        String userIdentifier = extractUserIdentifier(httpRequest);
        aiService.rateInteraction(identifier, request.getRating(), userIdentifier, request.getReason());
        return ResponseEntity.ok().build();
    }

    // ============================================
    // SOFT DELETE & RESTORE
    // ============================================

    @Operation(summary = "Soft delete AI interaction", description = "Soft-deletes an AI interaction. Admin or owner can delete.")
    @DeleteMapping("/{identifier}")
    public ResponseEntity<Void> deleteInteraction(
            @Parameter(description = "Interaction ID or Number") @PathVariable String identifier,
            @RequestParam(required = false) String reason,
            HttpServletRequest httpRequest) {

        String userIdentifier = extractUserIdentifier(httpRequest);
        aiService.softDelete(identifier, userIdentifier, reason);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Restore AI interaction", description = "Restores a previously soft-deleted interaction. Admin only.")
    @PostMapping("/{identifier}/restore")
    public ResponseEntity<AIInteractionResponse> restoreInteraction(
            @Parameter(description = "Interaction ID or Number") @PathVariable String identifier,
            HttpServletRequest httpRequest) {

        String userIdentifier = extractUserIdentifier(httpRequest);
        aiService.restore(identifier, userIdentifier);
        var interaction = aiService.findByIdentifier(identifier, userIdentifier);
        return ResponseEntity.ok(AIInteractionResponse.fromEntity(interaction));
    }

    // ============================================
    // STATISTICS
    // ============================================

    @Operation(summary = "Get AI usage statistics", description = "Returns average rating and total interaction count")
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats(HttpServletRequest httpRequest) {
        String userIdentifier = extractUserIdentifier(httpRequest);

        Map<String, Object> stats = new HashMap<>();
        stats.put("averageRating", aiService.getAverageRating(userIdentifier));
        stats.put("totalInteractions", aiService.getTotalInteractionsCount(userIdentifier));

        return ResponseEntity.ok(stats);
    }

    // ============================================
    // HELPER METHODS
    // ============================================

    private String extractUserIdentifier(HttpServletRequest request) {
        String token = extractToken(request);
        return jwtUtils.getEmailFromToken(token);
    }

    private String extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new RuntimeException("Missing or invalid Authorization header");
        }
        return authHeader.substring(7);
    }
}

