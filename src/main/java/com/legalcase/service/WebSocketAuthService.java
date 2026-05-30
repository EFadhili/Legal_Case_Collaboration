package com.legalcase.service;

import com.legalcase.entity.LegalCase;
import com.legalcase.entity.User;
import com.legalcase.repository.CaseMemberRepository;
import com.legalcase.repository.CaseRepository;
import com.legalcase.repository.UserRepository;
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
                return false;
            }

            // Find case by ID or case number
            Long caseId = findCaseId(caseIdentifier);

            if (caseId == null) {
                log.warn("Case not found: {}", caseIdentifier);
                return false;
            }

            // Check membership
            boolean isMember = caseMemberRepository.existsByLegalCaseIdAndUserId(caseId, userId);

            if (isMember) {
                log.debug("User {} is a member of case {}", userIdentifier, caseIdentifier);
            } else {
                log.warn("User {} is NOT a member of case {}", userIdentifier, caseIdentifier);
            }

            return isMember;

        } catch (Exception e) {
            log.error("Error checking case membership for user {} in case {}: {}",
                    userIdentifier, caseIdentifier, e.getMessage());
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