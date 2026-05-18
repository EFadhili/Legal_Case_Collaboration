package com.legalcase.controller;

import com.legalcase.dto.request.AIConversationRequest;
import com.legalcase.dto.request.AIQueryRequest;
import com.legalcase.dto.response.AIInteractionResponse;
import com.legalcase.dto.response.AIResponse;
import com.legalcase.entity.AIInteraction;
import com.legalcase.security.JwtUtils;
import com.legalcase.service.AIService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
@Slf4j
public class AIController {

    private final AIService aiService;
    private final JwtUtils jwtUtils;

    /**
     * Ask a question to the AI assistant.
     * POST /api/ai/query
     */
    @PostMapping("/query")
    public ResponseEntity<AIResponse> query(@Valid @RequestBody AIQueryRequest request,
                                            HttpServletRequest httpRequest) {
        Long userId = extractUserId(httpRequest);
        AIResponse response = aiService.processQuery(request, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * Multi-turn conversation with AI.
     * POST /api/ai/conversation
     */
    @PostMapping("/conversation")
    public ResponseEntity<AIResponse> conversation(@Valid @RequestBody AIConversationRequest request,
                                                   HttpServletRequest httpRequest) {
        Long userId = extractUserId(httpRequest);
        AIResponse response = aiService.processConversation(request, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * Get AI interaction history for the current user.
     * GET /api/ai/history?page=0&size=20
     */
    @GetMapping("/history")
    public ResponseEntity<Map<String, Object>> getHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest httpRequest) {

        Long userId = extractUserId(httpRequest);
        List<AIInteraction> interactions = aiService.getUserHistory(userId, page, size);

        // Convert to DTO to avoid lazy loading issues
        List<AIInteractionResponse> responses = interactions.stream()
                .map(AIInteractionResponse::fromEntity)
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("interactions", responses);
        response.put("page", page);
        response.put("size", size);

        return ResponseEntity.ok(response);
    }

    /**
     * Get AI interaction history for a specific case.
     * GET /api/ai/case/{caseId}/history
     */
    @GetMapping("/case/{caseId}/history")
    public ResponseEntity<List<AIInteractionResponse>> getCaseHistory(@PathVariable Long caseId,
                                                                      HttpServletRequest httpRequest) {
        extractUserId(httpRequest); // Verify authentication
        List<AIInteractionResponse> interactions = aiService.getCaseHistory(caseId);
        return ResponseEntity.ok(interactions);
    }

    /**
     * Rate an AI interaction (feedback).
     * POST /api/ai/rate/{interactionId}?rating=5
     */
    @PostMapping("/rate/{interactionId}")
    public ResponseEntity<Void> rateInteraction(@PathVariable Long interactionId,
                                                @RequestParam int rating,
                                                HttpServletRequest httpRequest) {
        Long userId = extractUserId(httpRequest);
        aiService.rateInteraction(interactionId, rating, userId);
        return ResponseEntity.ok().build();
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


