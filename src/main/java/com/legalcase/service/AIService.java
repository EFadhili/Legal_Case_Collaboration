package com.legalcase.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legalcase.dto.request.AIQueryRequest;
import com.legalcase.dto.request.AIConversationRequest;
import com.legalcase.dto.response.AIInteractionResponse;
import com.legalcase.dto.response.AIResponse;
import com.legalcase.entity.AIInteraction;
import com.legalcase.entity.Document;
import com.legalcase.entity.LegalCase;
import com.legalcase.entity.User;
import com.legalcase.enums.AIQueryType;
import com.legalcase.exception.*;
import com.legalcase.repository.AIInteractionRepository;
import com.legalcase.repository.CaseRepository;
import com.legalcase.repository.DocumentRepository;
import com.legalcase.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
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
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${gemini.api.key:}")
    private String geminiApiKey;

    @Value("${gemini.api.url:https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent}")
    private String geminiApiUrl;

    private static final int MAX_CONTEXT_DOCUMENTS = 5;
    private static final int MAX_DOCUMENT_TEXT_LENGTH = 50000;

    /**
     * Process an AI query with optional case and document context.
     */
    @Transactional
    public AIResponse processQuery(AIQueryRequest request, Long userId) {
        long startTime = System.currentTimeMillis();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        LegalCase legalCase = null;
        StringBuilder contextBuilder = new StringBuilder();

        // Build context from case
        if (request.getCaseId() != null) {
            legalCase = caseRepository.findById(request.getCaseId())
                    .orElseThrow(() -> new ResourceNotFoundException("Case", request.getCaseId()));

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

        // Build context from documents
        Set<Long> documentIds = request.getDocumentIds();
        if (documentIds != null && !documentIds.isEmpty()) {
            List<Document> documents = documentRepository.findAllById(documentIds);
            documents = documents.stream()
                    .filter(d -> !d.isDeleted())
                    .limit(MAX_CONTEXT_DOCUMENTS)
                    .collect(Collectors.toList());

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

        return AIResponse.fromEntity(interaction);
    }

    /**
     * Multi-turn conversation with AI.
     */
    @Transactional
    public AIResponse processConversation(AIConversationRequest request, Long userId) {
        long startTime = System.currentTimeMillis();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        LegalCase legalCase = null;
        String caseContext = "";

        if (request.getCaseId() != null) {
            legalCase = caseRepository.findById(request.getCaseId())
                    .orElseThrow(() -> new ResourceNotFoundException("Case", request.getCaseId()));
            caseContext = buildCaseContext(legalCase);
        }

        String conversationPrompt = buildConversationPrompt(request.getMessage(), request.getHistory(), caseContext);
        String aiResponse = callGeminiApi(conversationPrompt);

        long processingTime = System.currentTimeMillis() - startTime;

        AIInteraction interaction = new AIInteraction();
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

    private int estimateTokenCount(String text) {
        return text.length() / 4;
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

    /**
     * Get AI interaction history for a user (returns DTOs).
     */
    public List<AIInteractionResponse> getUserHistory(Long userId, int page, int size) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        List<AIInteraction> interactions = aiInteractionRepository.findByUserWithDetails(user);

        return interactions.stream()
                .skip((long) page * size)
                .limit(size)
                .map(AIInteractionResponse::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Get AI interaction history for a case (returns DTOs).
     */
    public List<AIInteractionResponse> getCaseHistory(Long caseId) {
        LegalCase legalCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Case", caseId));

        List<AIInteraction> interactions = aiInteractionRepository.findByLegalCaseWithDetails(legalCase);

        return interactions.stream()
                .map(AIInteractionResponse::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Rate an AI interaction (feedback).
     */
    @Transactional
    public void rateInteraction(Long interactionId, Integer rating, Long userId) {
        AIInteraction interaction = aiInteractionRepository.findById(interactionId)
                .orElseThrow(() -> new ResourceNotFoundException("AI Interaction", interactionId));

        if (!interaction.getUser().getId().equals(userId)) {
            throw new UnauthorizedException("You can only rate your own interactions");
        }

        if (rating < 1 || rating > 5) {
            throw new ValidationException("rating", "Rating must be between 1 and 5");
        }

        interaction.setUserRating(rating);
        aiInteractionRepository.save(interaction);
    }
}