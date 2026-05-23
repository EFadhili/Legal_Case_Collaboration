package com.legalcase.controller;

import com.legalcase.dto.request.AIConversationRequest;
import com.legalcase.dto.request.AIQueryRequest;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
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

    @Operation(
            summary = "Ask a question to the AI assistant",
            description = "Sends a prompt to Google Gemini AI with optional case and document context. Supports document analysis, summarization, clause extraction, risk analysis, and contract review."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "AI response generated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request format"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid or missing token"),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded - Too many requests"),
            @ApiResponse(responseCode = "500", description = "Gemini API error or internal server error")
    })
    @PostMapping("/query")
    public ResponseEntity<AIResponse> query(
            @Valid @RequestBody AIQueryRequest request,
            HttpServletRequest httpRequest) {

        Long userId = extractUserId(httpRequest);
        AIResponse response = aiService.processQuery(request, userId);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Multi-turn conversation with AI",
            description = "Continues a conversation with AI, maintaining chat history. Useful for follow-up questions and iterative analysis."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "AI response generated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request format"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    })
    @PostMapping("/conversation")
    public ResponseEntity<AIResponse> conversation(
            @Valid @RequestBody AIConversationRequest request,
            HttpServletRequest httpRequest) {

        Long userId = extractUserId(httpRequest);
        AIResponse response = aiService.processConversation(request, userId);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Get AI interaction history",
            description = "Returns the current user's previous AI queries and responses. Useful for revisiting past analyses."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "History retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/history")
    public ResponseEntity<Map<String, Object>> getHistory(
            @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Number of items per page") @RequestParam(defaultValue = "20") int size,
            HttpServletRequest httpRequest) {

        Long userId = extractUserId(httpRequest);
        List<AIInteractionResponse> interactions = aiService.getUserHistory(userId, page, size);

        Map<String, Object> response = new HashMap<>();
        response.put("interactions", interactions);
        response.put("page", page);
        response.put("size", size);
        response.put("total", interactions.size());

        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Get AI history for a specific case",
            description = "Returns all AI interactions related to a specific case. Useful for case-specific context."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "History retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Case not found")
    })
    @GetMapping("/case/{caseId}/history")
    public ResponseEntity<List<AIInteractionResponse>> getCaseHistory(
            @Parameter(description = "Case ID") @PathVariable Long caseId,
            HttpServletRequest httpRequest) {

        extractUserId(httpRequest);
        List<AIInteractionResponse> interactions = aiService.getCaseHistory(caseId);
        return ResponseEntity.ok(interactions);
    }

    @Operation(
            summary = "Rate an AI interaction",
            description = "Provides feedback on AI response quality. Rating from 1 to 5 stars. Helps improve the AI assistant."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Rating submitted successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid rating value (must be 1-5)"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Cannot rate another user's interaction"),
            @ApiResponse(responseCode = "404", description = "Interaction not found")
    })
    @PostMapping("/rate/{interactionId}")
    public ResponseEntity<Void> rateInteraction(
            @Parameter(description = "AI Interaction ID") @PathVariable Long interactionId,
            @Parameter(description = "Rating (1-5 stars)") @RequestParam int rating,
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