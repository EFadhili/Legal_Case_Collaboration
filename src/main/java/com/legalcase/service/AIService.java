package com.legalcase.service;

import com.legalcase.service.NotificationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legalcase.dto.request.AIQueryRequest;
import com.legalcase.dto.request.AIConversationRequest;
import com.legalcase.dto.response.AIInteractionResponse;
import com.legalcase.dto.response.AIResponse;
import com.legalcase.entity.*;
import com.legalcase.enums.AIQueryType;
import com.legalcase.enums.Role;
import com.legalcase.exception.*;
import com.legalcase.repository.*;
import com.legalcase.util.AuditContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.Year;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AIService {

    private final AIInteractionRepository aiInteractionRepository;
    private final UserRepository userRepository;
    private final CaseRepository caseRepository;
    private final DocumentRepository documentRepository;
    private final CaseMemberRepository caseMemberRepository;
    private final NotificationService notificationService;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;  // ADDED


    @Value("${gemini.api.key:}")
    private String geminiApiKey;

    @Value("${gemini.api.url:https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent}")
    private String geminiApiUrl;

    @Value("${gemini.api.stream-url:https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:streamGenerateContent}")
    private String geminiStreamApiUrl;

    private static final int MAX_CONTEXT_DOCUMENTS = 5;
    private static final int MAX_DOCUMENT_TEXT_LENGTH = 50000;

    // ============================================
    // HELPER METHODS
    // ============================================

    private void recordAudit(com.legalcase.enums.AuditAction action,
                             com.legalcase.enums.EntityType entityType,
                             Long entityId, String entityIdentifier,
                             Object beforeState, Object afterState,
                             String details, boolean success, String errorMessage) {
        auditService.recordAuditAsync(
                AuditContext.getCurrentUserId(),
                AuditContext.getCurrentUserIdentifier(),
                AuditContext.getCurrentUserName(),
                action,
                entityType,
                entityId,
                entityIdentifier,
                beforeState,
                afterState,
                details,
                success ? com.legalcase.enums.AuditStatus.SUCCESS : com.legalcase.enums.AuditStatus.FAILURE,
                errorMessage,
                AuditContext.getCurrentIpAddress(),
                AuditContext.getCurrentUserAgent()
        );
    }

    private User findUserByIdentifier(String identifier) {
        return userRepository.findByUsername(identifier)
                .or(() -> userRepository.findByEmail(identifier))
                .orElseThrow(() -> new ResourceNotFoundException("User", "username or email", identifier));
    }

    private LegalCase findCaseByIdentifier(String identifier) {
        try {
            Long id = Long.parseLong(identifier);
            return caseRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Case", id));
        } catch (NumberFormatException e) {
            return caseRepository.findByCaseNumberWithDetails(identifier)
                    .orElseThrow(() -> new ResourceNotFoundException("Case", "caseNumber", identifier));
        }
    }

    private void verifyCaseAccess(LegalCase legalCase, User user) {
        boolean isAdmin = user.getRole() == Role.ADMIN;
        boolean isLawyer = user.getRole() == Role.LAWYER;

        if (isAdmin) {
            return;
        }

        if (isLawyer) {
            // Lawyers can access cases they own or are assigned to
            boolean isOwner = legalCase.getOwner().getId().equals(user.getId());
            boolean isAssigned = caseMemberRepository.existsByLegalCaseAndUser(legalCase, user);
            if (isOwner || isAssigned) {
                return;
            }
        }

        // Staff can only access cases they are members of
        boolean isMember = caseMemberRepository.existsByLegalCaseAndUser(legalCase, user);
        if (isMember) {
            return;
        }

        throw new AccessDeniedException("You don't have access to this case");
    }

    private void verifyDocumentAccess(Document document, User user) {
        LegalCase legalCase = document.getLegalCase();
        if (legalCase == null && document.getTask() != null) {
            legalCase = document.getTask().getLegalCase();
        }

        if (legalCase == null) {
            throw new BusinessException("Document has no associated case");
        }

        verifyCaseAccess(legalCase, user);
    }

    private void verifyAiInteractionAccess(AIInteraction interaction, User user) {
        boolean isAdmin = user.getRole() == Role.ADMIN;
        boolean isOwner = interaction.getUser().getId().equals(user.getId());

        if (isAdmin || isOwner) {
            return;
        }

        // Lawyers can access interactions related to their cases
        if (user.getRole() == Role.LAWYER && interaction.getLegalCase() != null) {
            verifyCaseAccess(interaction.getLegalCase(), user);
            return;
        }

        throw new AccessDeniedException("You don't have access to this AI interaction");
    }

    private String generateInteractionNumber() {
        String year = String.valueOf(Year.now());
        long count = aiInteractionRepository.count() + 1;
        return "AI-" + year + "-" + String.format("%06d", count);
    }

    private int estimateTokenCount(String text) {
        return text == null ? 0 : text.length() / 4;
    }

    // ============================================
    // AI QUERY PROCESSING
    // ============================================

    @Transactional
    public AIResponse processQuery(AIQueryRequest request, String userIdentifier) {
        long startTime = System.currentTimeMillis();

        User user = findUserByIdentifier(userIdentifier);
        LegalCase legalCase = null;
        StringBuilder contextBuilder = new StringBuilder();

        // Verify case access and build context
        if (request.getCaseId() != null) {
            legalCase = caseRepository.findById(request.getCaseId())
                    .orElseThrow(() -> new ResourceNotFoundException("Case", request.getCaseId()));
            verifyCaseAccess(legalCase, user);

            contextBuilder.append("Case Context:\n");
            contextBuilder.append("- Case Title: ").append(legalCase.getTitle()).append("\n");
            contextBuilder.append("- Case Number: ").append(legalCase.getCaseNumber()).append("\n");
            contextBuilder.append("- Case Type: ").append(legalCase.getType()).append("\n");
            contextBuilder.append("- Case Status: ").append(legalCase.getStatus()).append("\n");
            if (legalCase.getDescription() != null && !legalCase.getDescription().isEmpty()) {
                contextBuilder.append("- Case Description: ").append(legalCase.getDescription()).append("\n");
            }
            contextBuilder.append("\n");
        }

        // Build context from documents with access verification
        Set<Long> documentIds = request.getDocumentIds();
        if (documentIds != null && !documentIds.isEmpty()) {
            List<Document> documents = documentRepository.findAllById(documentIds);
            documents = documents.stream()
                    .filter(d -> !d.isDeleted())
                    .limit(MAX_CONTEXT_DOCUMENTS)
                    .collect(Collectors.toList());

            // Verify access to each document
            for (Document doc : documents) {
                verifyDocumentAccess(doc, user);
            }

            contextBuilder.append("Document Context:\n");
            for (Document doc : documents) {
                contextBuilder.append("\n--- Document: ").append(doc.getOriginalFileName()).append(" ---\n");
                String text = doc.getExtractedText();
                if (text != null && !text.isEmpty()) {
                    if (text.length() > MAX_DOCUMENT_TEXT_LENGTH) {
                        text = text.substring(0, MAX_DOCUMENT_TEXT_LENGTH) + "...\n[Document truncated due to length]";
                    }
                    contextBuilder.append(text).append("\n");
                } else {
                    contextBuilder.append("[No extracted text available for this document]\n");
                }
            }
            contextBuilder.append("\n");
        }

        // Build the prompt
        String fullPrompt = buildPrompt(request.getPrompt(), contextBuilder.toString(), request.getQueryType());

        // Call Gemini API
        String aiResponse = callGeminiApi(fullPrompt);

        long processingTime = System.currentTimeMillis() - startTime;

        // Save interaction
        AIInteraction interaction = new AIInteraction();
        interaction.setInteractionNumber(generateInteractionNumber());
        interaction.setUser(user);
        interaction.setLegalCase(legalCase);
        interaction.setQueryType(request.getQueryType());
        interaction.setUserPrompt(request.getPrompt());
        interaction.setAiResponse(aiResponse);
        interaction.setResponseFormat(request.getResponseFormat());
        interaction.setModelUsed("gemini-2.5-flash");
        interaction.setTokenCountInput(estimateTokenCount(fullPrompt));
        interaction.setTokenCountOutput(estimateTokenCount(aiResponse));
        interaction.setProcessingTimeMs(processingTime);

        if (documentIds != null && !documentIds.isEmpty()) {
            interaction.setContextDocumentIdsFromSet(documentIds);
        }

        aiInteractionRepository.save(interaction);

        if (request.getDocumentIds() != null && !request.getDocumentIds().isEmpty()) {
            notificationService.notifyAIAnalysisComplete(
                    interaction.getId(),
                    user.getId(),
                    interaction.getInteractionNumber()
            );
        }

        // AUDIT: AI query performed
        recordAudit(com.legalcase.enums.AuditAction.AI_QUERY,
                com.legalcase.enums.EntityType.AI_INTERACTION,
                interaction.getId(),
                interaction.getInteractionNumber(),
                request,
                interaction,
                "Query type: " + request.getQueryType(),
                true,
                null);

        return AIResponse.fromEntity(interaction);
    }

    /**
     * Stream AI response (for WebSocket)
     */
    public Flux<String> streamQuery(AIQueryRequest request, String userIdentifier, String sessionId) {
        User user = findUserByIdentifier(userIdentifier);

        // Verify access (same as processQuery)
        if (request.getCaseId() != null) {
            LegalCase legalCase = caseRepository.findById(request.getCaseId())
                    .orElseThrow(() -> new ResourceNotFoundException("Case", request.getCaseId()));
            verifyCaseAccess(legalCase, user);
        }

        String fullPrompt = buildPrompt(request.getPrompt(), "", request.getQueryType());

        return callGeminiApiStream(fullPrompt);
    }

    @Transactional
    public AIResponse processConversation(AIConversationRequest request, String userIdentifier) {
        long startTime = System.currentTimeMillis();

        User user = findUserByIdentifier(userIdentifier);
        LegalCase legalCase = null;
        String caseContext = "";

        if (request.getCaseId() != null) {
            legalCase = caseRepository.findById(request.getCaseId())
                    .orElseThrow(() -> new ResourceNotFoundException("Case", request.getCaseId()));
            verifyCaseAccess(legalCase, user);
            caseContext = buildCaseContext(legalCase);
        }

        String conversationPrompt = buildConversationPrompt(request.getMessage(), request.getHistory(), caseContext);
        String aiResponse = callGeminiApi(conversationPrompt);

        long processingTime = System.currentTimeMillis() - startTime;

        AIInteraction interaction = new AIInteraction();
        interaction.setInteractionNumber(generateInteractionNumber());
        interaction.setUser(user);
        interaction.setLegalCase(legalCase);
        interaction.setQueryType(AIQueryType.GENERAL_QUESTION);
        interaction.setUserPrompt(request.getMessage());
        interaction.setAiResponse(aiResponse);
        interaction.setModelUsed("gemini-2.5-flash");
        interaction.setTokenCountInput(estimateTokenCount(conversationPrompt));
        interaction.setTokenCountOutput(estimateTokenCount(aiResponse));
        interaction.setProcessingTimeMs(processingTime);

        aiInteractionRepository.save(interaction);

        // AUDIT: AI conversation (query)
        recordAudit(com.legalcase.enums.AuditAction.AI_QUERY,
                com.legalcase.enums.EntityType.AI_INTERACTION,
                interaction.getId(),
                interaction.getInteractionNumber(),
                request,
                interaction,
                "Conversation query",
                true,
                null);

        return AIResponse.fromEntity(interaction);
    }

    private String buildPrompt(String userPrompt, String context, AIQueryType queryType) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("You are an AI legal assistant for a Legal Case Management Platform. ");
        prompt.append("Your role is to help legal professionals analyze documents, understand cases, ");
        prompt.append("and provide general legal information. ");
        prompt.append("Always include a disclaimer that this is not legal advice.\n\n");

        if (context != null && !context.isEmpty()) {
            prompt.append("CONTEXT PROVIDED:\n");
            prompt.append(context);
            prompt.append("\n");
        }

        switch (queryType) {
            case DOCUMENT_SUMMARY:
                prompt.append("Please provide a concise summary of the document(s) above. ");
                prompt.append("Include key points, important clauses, and potential concerns.\n\n");
                break;
            case CLAUSE_EXTRACTION:
                prompt.append("Please extract and list all important clauses from the document(s). ");
                prompt.append("For each clause, provide a brief explanation of its implications.\n\n");
                break;
            case RISK_ANALYSIS:
                prompt.append("Please analyze the document(s) for potential legal risks. ");
                prompt.append("Identify high-risk clauses, ambiguous language, and potential liabilities.\n\n");
                break;
            case CONTRACT_REVIEW:
                prompt.append("Please review this contract and identify: ");
                prompt.append("1) Key obligations of each party, ");
                prompt.append("2) Potential issues or ambiguities, ");
                prompt.append("3) Missing standard clauses, ");
                prompt.append("4) Recommendations for improvement.\n\n");
                break;
            case COMPARISON:
                prompt.append("Please compare the provided documents. ");
                prompt.append("Highlight similarities, differences, and any conflicting terms.\n\n");
                break;
            default:
                prompt.append("Please answer the following legal question based on the context provided.\n\n");
                break;
        }

        prompt.append("USER QUESTION:\n");
        prompt.append(userPrompt);
        prompt.append("\n\n");
        prompt.append("Please provide a clear, professional response. ");
        prompt.append("If you're unsure about something, state that clearly. ");
        prompt.append("Remember to include the disclaimer at the end of your response.");

        return prompt.toString();
    }

    private String buildCaseContext(LegalCase legalCase) {
        StringBuilder context = new StringBuilder();
        context.append("Case Information:\n");
        context.append("- Title: ").append(legalCase.getTitle()).append("\n");
        context.append("- Case Number: ").append(legalCase.getCaseNumber()).append("\n");
        context.append("- Type: ").append(legalCase.getType()).append("\n");
        context.append("- Status: ").append(legalCase.getStatus()).append("\n");
        context.append("- Priority: ").append(legalCase.getPriority()).append("\n");
        if (legalCase.getDescription() != null) {
            context.append("- Description: ").append(legalCase.getDescription()).append("\n");
        }
        return context.toString();
    }

    private String buildConversationPrompt(String message, List<AIConversationRequest.ChatHistory> history, String caseContext) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("You are an AI legal assistant. Be helpful, professional, and accurate.\n\n");

        if (caseContext != null && !caseContext.isEmpty()) {
            prompt.append(caseContext);
            prompt.append("\n");
        }

        if (history != null && !history.isEmpty()) {
            prompt.append("Conversation History:\n");
            for (AIConversationRequest.ChatHistory entry : history) {
                prompt.append(entry.getRole()).append(": ").append(entry.getContent()).append("\n");
            }
            prompt.append("\n");
        }

        prompt.append("User: ").append(message).append("\n");
        prompt.append("Assistant: ");

        return prompt.toString();
    }

    private String callGeminiApi(String prompt) {
        if (geminiApiKey == null || geminiApiKey.isEmpty()) {
            log.warn("Gemini API key not configured. Returning mock response.");
            return getMockResponse(prompt);
        }

        try {
            Map<String, Object> requestBody = buildGeminiRequest(prompt);

            String response = webClient.post()
                    .uri(geminiApiUrl + "?key=" + geminiApiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            clientResponse -> clientResponse.bodyToMono(String.class)
                                    .flatMap(errorBody -> {
                                        log.error("Gemini API error: {}", errorBody);
                                        return Mono.error(new FileProcessingException("Gemini API error: " + errorBody));
                                    }))
                    .bodyToMono(String.class)
                    .block();

            if (response == null) {
                throw new FileProcessingException("No response from Gemini API");
            }

            return extractResponseText(response);

        } catch (Exception e) {
            log.error("Error calling Gemini API: {}", e.getMessage());
            return getErrorResponse(e.getMessage());
        }
    }

    private Flux<String> callGeminiApiStream(String prompt) {
        if (geminiApiKey == null || geminiApiKey.isEmpty()) {
            log.warn("Gemini API key not configured. Returning mock stream.");
            return Flux.just(getMockResponse(prompt));
        }

        Map<String, Object> requestBody = buildGeminiRequest(prompt);

        return webClient.post()
                .uri(geminiStreamApiUrl + "?key=" + geminiApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToFlux(String.class)
                .map(this::extractStreamChunk)
                .onErrorResume(e -> {
                    log.error("Stream error: {}", e.getMessage());
                    return Flux.just(getErrorResponse(e.getMessage()));
                });
    }

    private Map<String, Object> buildGeminiRequest(String prompt) {
        Map<String, Object> request = new HashMap<>();

        List<Map<String, Object>> contents = new ArrayList<>();
        Map<String, Object> content = new HashMap<>();
        content.put("role", "user");

        List<Map<String, Object>> parts = new ArrayList<>();
        Map<String, Object> part = new HashMap<>();
        part.put("text", prompt);
        parts.add(part);

        content.put("parts", parts);
        contents.add(content);
        request.put("contents", contents);

        Map<String, Object> generationConfig = new HashMap<>();
        generationConfig.put("temperature", 0.7);
        generationConfig.put("maxOutputTokens", 4096);
        generationConfig.put("topP", 0.95);
        request.put("generationConfig", generationConfig);

        return request;
    }

    private String extractResponseText(String responseJson) {
        try {
            JsonNode root = objectMapper.readTree(responseJson);
            JsonNode candidates = root.path("candidates");
            if (candidates.isArray() && candidates.size() > 0) {
                JsonNode content = candidates.get(0).path("content");
                JsonNode parts = content.path("parts");
                if (parts.isArray() && parts.size() > 0) {
                    String text = parts.get(0).path("text").asText();
                    if (text != null && !text.isEmpty()) {
                        return text;
                    }
                }
            }
            return "I apologize, but I couldn't generate a proper response. Please try again.";
        } catch (Exception e) {
            log.error("Error parsing Gemini response: {}", e.getMessage());
            return "Error processing response. Please try again.";
        }
    }

    private String extractStreamChunk(String chunk) {
        try {
            JsonNode root = objectMapper.readTree(chunk);
            JsonNode candidates = root.path("candidates");
            if (candidates.isArray() && candidates.size() > 0) {
                JsonNode content = candidates.get(0).path("content");
                JsonNode parts = content.path("parts");
                if (parts.isArray() && parts.size() > 0) {
                    return parts.get(0).path("text").asText();
                }
            }
            return "";
        } catch (Exception e) {
            log.error("Error parsing stream chunk: {}", e.getMessage());
            return "";
        }
    }

    private String getMockResponse(String prompt) {
        return "This is a mock response. To use the actual Gemini API, please configure your API key in application.properties.\n\n" +
                "Your question was: " + (prompt.length() > 200 ? prompt.substring(0, 200) + "..." : prompt) + "\n\n" +
                "DISCLAIMER: This is a mock response. Please configure your Gemini API key to get real AI responses.";
    }

    private String getErrorResponse(String error) {
        return "I encountered an error while processing your request: " + error + "\n\n" +
                "Please try again later. If the issue persists, contact support.\n\n" +
                "DISCLAIMER: This is for informational purposes only and does not constitute legal advice.";
    }

    // ============================================
    // GET HISTORY METHODS
    // ============================================

    public Page<AIInteractionResponse> getUserHistory(String userIdentifier, Pageable pageable) {
        User user = findUserByIdentifier(userIdentifier);
        Page<AIInteraction> interactions = aiInteractionRepository.findByUser(user, pageable);
        return interactions.map(AIInteractionResponse::fromEntity);
    }

    public List<AIInteractionResponse> getCaseHistory(String caseIdentifier, String userIdentifier) {
        LegalCase legalCase = findCaseByIdentifier(caseIdentifier);
        User user = findUserByIdentifier(userIdentifier);
        verifyCaseAccess(legalCase, user);

        List<AIInteraction> interactions = aiInteractionRepository.findByLegalCaseOrderByCreatedAtDesc(legalCase);
        return interactions.stream()
                .map(AIInteractionResponse::fromEntity)
                .collect(Collectors.toList());
    }

    public Page<AIInteractionResponse> searchUserHistory(String userIdentifier, String searchTerm, Pageable pageable) {
        User user = findUserByIdentifier(userIdentifier);
        Page<AIInteraction> interactions = aiInteractionRepository.searchByUser(user, searchTerm, pageable);
        return interactions.map(AIInteractionResponse::fromEntity);
    }

    public Page<AIInteractionResponse> searchCaseHistory(String caseIdentifier, String userIdentifier, String searchTerm, Pageable pageable) {
        LegalCase legalCase = findCaseByIdentifier(caseIdentifier);
        User user = findUserByIdentifier(userIdentifier);
        verifyCaseAccess(legalCase, user);

        Page<AIInteraction> interactions = aiInteractionRepository.searchByLegalCase(legalCase, searchTerm, pageable);
        return interactions.map(AIInteractionResponse::fromEntity);
    }

    public Page<AIInteractionResponse> adminGlobalSearch(String userIdentifier, String searchTerm, Pageable pageable) {
        User user = findUserByIdentifier(userIdentifier);
        if (user.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Only admins can perform global search");
        }
        Page<AIInteraction> interactions = aiInteractionRepository.adminGlobalSearch(searchTerm, pageable);
        return interactions.map(AIInteractionResponse::fromEntity);
    }

    public AIInteraction findByIdentifier(String identifier, String userIdentifier) {
        User user = findUserByIdentifier(userIdentifier);
        AIInteraction interaction;

        try {
            Long id = Long.parseLong(identifier);
            interaction = aiInteractionRepository.findByIdAndIsDeletedFalse(id)
                    .orElseThrow(() -> new ResourceNotFoundException("AI Interaction", id));
        } catch (NumberFormatException e) {
            interaction = aiInteractionRepository.findByInteractionNumberAndIsDeletedFalse(identifier)
                    .orElseThrow(() -> new ResourceNotFoundException("AI Interaction", "interactionNumber", identifier));
        }

        verifyAiInteractionAccess(interaction, user);
        return interaction;
    }

    // ============================================
    // RATING METHODS
    // ============================================

    @Transactional
    public void rateInteraction(String identifier, Integer rating, String userIdentifier, String reason) {
        User user = findUserByIdentifier(userIdentifier);
        AIInteraction interaction = findByIdentifier(identifier, userIdentifier);

        if (!interaction.getUser().getId().equals(user.getId())) {
            throw new UnauthorizedException("You can only rate your own interactions");
        }

        if (rating < 1 || rating > 5) {
            throw new ValidationException("rating", "Rating must be between 1 and 5");
        }

        LocalDateTime now = LocalDateTime.now();
        Integer oldRating = interaction.getUserRating();

        String historyRecord = String.format(
                "{\"timestamp\":\"%s\",\"oldRating\":%d,\"newRating\":%d,\"changedBy\":%d,\"reason\":\"%s\"}|",
                now.toString(), oldRating != null ? oldRating : 0, rating, user.getId(),
                reason != null ? reason.replace("\"", "\\\"") : ""
        );

        aiInteractionRepository.updateRating(interaction.getId(), rating, now, user.getId(), historyRecord);

        // AUDIT: AI rating changed
        recordAudit(com.legalcase.enums.AuditAction.AI_RATING,
                com.legalcase.enums.EntityType.AI_INTERACTION,
                interaction.getId(),
                interaction.getInteractionNumber(),
                oldRating,
                rating,
                "Rating changed from " + oldRating + " to " + rating,
                true,
                null);
    }

    // ============================================
    // SOFT DELETE METHODS
    // ============================================

    @Transactional
    public void softDelete(String identifier, String userIdentifier, String reason) {
        User user = findUserByIdentifier(userIdentifier);
        AIInteraction interaction = findByIdentifier(identifier, userIdentifier);

        boolean isAdmin = user.getRole() == Role.ADMIN;
        boolean isOwner = interaction.getUser().getId().equals(user.getId());

        if (!isAdmin && !isOwner) {
            throw new AccessDeniedException("You don't have permission to delete this interaction");
        }

        String deleteReason = (reason != null && !reason.isEmpty()) ? reason : "No reason provided";
        aiInteractionRepository.softDelete(interaction.getId(), LocalDateTime.now(), user.getId(), deleteReason);

        // AUDIT: AI interaction soft deleted
        recordAudit(com.legalcase.enums.AuditAction.AI_QUERY,
                com.legalcase.enums.EntityType.AI_INTERACTION,
                interaction.getId(),
                interaction.getInteractionNumber(),
                interaction,
                null,
                "Soft deleted by " + userIdentifier + ", reason: " + deleteReason,
                true,
                null);
    }

    @Transactional
    public void restore(String identifier, String userIdentifier) {
        User user = findUserByIdentifier(userIdentifier);

        if (user.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Only admins can restore deleted interactions");
        }

        AIInteraction interaction;
        try {
            Long id = Long.parseLong(identifier);
            interaction = aiInteractionRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("AI Interaction", id));
        } catch (NumberFormatException e) {
            interaction = aiInteractionRepository.findByInteractionNumberAndIsDeletedFalse(identifier)
                    .orElseThrow(() -> new ResourceNotFoundException("AI Interaction", "interactionNumber", identifier));
        }

        aiInteractionRepository.restore(interaction.getId());

        // AUDIT: AI interaction restored
        recordAudit(com.legalcase.enums.AuditAction.AI_QUERY,
                com.legalcase.enums.EntityType.AI_INTERACTION,
                interaction.getId(),
                interaction.getInteractionNumber(),
                null,
                interaction,
                "Restored by admin " + userIdentifier,
                true,
                null);
    }

    // ============================================
    // STATISTICS
    // ============================================

    public Double getAverageRating(String userIdentifier) {
        User user = findUserByIdentifier(userIdentifier);
        return aiInteractionRepository.getAverageUserRating(user);
    }

    public long getTotalInteractionsCount(String userIdentifier) {
        User user = findUserByIdentifier(userIdentifier);
        return aiInteractionRepository.countByUserAndIsDeletedFalse(user);
    }


}