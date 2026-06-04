package com.legalcase.service;

import com.legalcase.entity.LegalCase;
import com.legalcase.entity.User;
import com.legalcase.repository.CaseMemberRepository;
import com.legalcase.repository.CaseRepository;
import com.legalcase.repository.UserRepository;
import com.legalcase.util.AuditContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebSocketAuthService {

    private final CaseMemberRepository caseMemberRepository;
    private final CaseRepository caseRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;  // ADDED

    // ============================================
    // HELPER METHOD
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

    /**
     * Check if a user can access a case chat.
     *
     * @param caseIdentifier Case ID or Case Number
     * @param userIdentifier Username or Email
     * @return true if user is a case member, false otherwise
     */
    public boolean canAccessCaseChat(String caseIdentifier, String userIdentifier) {
        try {
            // Find user by username or email
            Long userId = userRepository.findByUsername(userIdentifier)
                    .or(() -> userRepository.findByEmail(userIdentifier))
                    .map(User::getId)
                    .orElse(null);

            if (userId == null) {
                log.warn("User not found: {}", userIdentifier);
                // AUDIT: User not found during WebSocket authentication
                recordAudit(com.legalcase.enums.AuditAction.ACCESS_DENIED,
                        com.legalcase.enums.EntityType.USER,
                        null,
                        userIdentifier,
                        null,
                        null,
                        "WebSocket access denied: User not found for case: " + caseIdentifier,
                        false,
                        "User not found");
                return false;
            }

            // Find case by ID or case number
            Long caseId = findCaseId(caseIdentifier);

            if (caseId == null) {
                log.warn("Case not found: {}", caseIdentifier);
                // AUDIT: Case not found during WebSocket authentication
                recordAudit(com.legalcase.enums.AuditAction.ACCESS_DENIED,
                        com.legalcase.enums.EntityType.CASE,
                        null,
                        caseIdentifier,
                        null,
                        null,
                        "WebSocket access denied: Case not found for user: " + userIdentifier,
                        false,
                        "Case not found");
                return false;
            }

            // Check membership
            boolean isMember = caseMemberRepository.existsByLegalCaseIdAndUserId(caseId, userId);

            if (isMember) {
                log.debug("User {} is a member of case {}", userIdentifier, caseIdentifier);
            } else {
                log.warn("User {} is NOT a member of case {}", userIdentifier, caseIdentifier);
                // AUDIT: Non-member attempted WebSocket access
                recordAudit(com.legalcase.enums.AuditAction.ACCESS_DENIED,
                        com.legalcase.enums.EntityType.CASE,
                        caseId,
                        caseIdentifier,
                        null,
                        null,
                        "WebSocket access denied: User " + userIdentifier + " is not a member of case " + caseIdentifier,
                        false,
                        "User is not a case member");
            }

            return isMember;

        } catch (Exception e) {
            log.error("Error checking case membership for user {} in case {}: {}",
                    userIdentifier, caseIdentifier, e.getMessage());
            // AUDIT: System error during WebSocket authentication
            recordAudit(com.legalcase.enums.AuditAction.ACCESS_DENIED,
                    com.legalcase.enums.EntityType.CASE,
                    null,
                    caseIdentifier,
                    null,
                    null,
                    "WebSocket access denied: System error for user " + userIdentifier,
                    false,
                    e.getMessage());
            return false;
        }
    }

    private Long findCaseId(String caseIdentifier) {
        try {
            // Try as numeric ID first
            return Long.parseLong(caseIdentifier);
        } catch (NumberFormatException e) {
            // Try as case number
            return caseRepository.findByCaseNumberWithDetails(caseIdentifier)
                    .map(LegalCase::getId)
                    .orElse(null);
        }
    }
}