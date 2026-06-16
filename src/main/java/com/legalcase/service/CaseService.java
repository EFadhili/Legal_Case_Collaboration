package com.legalcase.service;

import com.legalcase.exception.*;
import com.legalcase.dto.response.MemberResponse;
import com.legalcase.entity.CaseMember;
import com.legalcase.entity.LegalCase;
import com.legalcase.entity.User;
import com.legalcase.enums.*;
import com.legalcase.repository.CaseMemberRepository;
import com.legalcase.repository.CaseRepository;
import com.legalcase.repository.UserRepository;
import com.legalcase.util.AuditContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class CaseService {

    private final CaseRepository caseRepository;
    private final CaseMemberRepository caseMemberRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;

    // ============================================
    // HELPER METHODS
    // ============================================

    private void recordAudit(AuditAction action, EntityType entityType,
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
                success ? AuditStatus.SUCCESS : AuditStatus.FAILURE,
                errorMessage,
                AuditContext.getCurrentIpAddress(),
                AuditContext.getCurrentUserAgent()
        );
    }

    private LegalCase findCase(String identifier) {
        if (identifier == null || identifier.trim().isEmpty()) {
            throw new IllegalArgumentException("Case identifier is required");
        }
        try {
            Long id = Long.parseLong(identifier);
            return findById(id);
        } catch (NumberFormatException e) {
            return findByCaseNumber(identifier);
        }
    }

    private LegalCase findById(Long id) {
        return caseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Case not found with ID: " + id));
    }

    private LegalCase findByCaseNumber(String caseNumber) {
        return caseRepository.findByCaseNumberWithDetails(caseNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Case not found with number: " + caseNumber));
    }

    private User findUserByIdentifier(String identifier) {
        return userRepository.findByUsername(identifier)
                .or(() -> userRepository.findByEmail(identifier))
                .orElseThrow(() -> new ResourceNotFoundException("User not found with username or email: " + identifier));
    }

    private User findUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));
    }

    private Long getUserIdFromIdentifier(String userIdentifier) {
        return findUserByIdentifier(userIdentifier).getId();
    }

    private void verifyCaseAccess(LegalCase legalCase, String userIdentifier) {
        User user = findUserByIdentifier(userIdentifier);

        if (!caseMemberRepository.existsByLegalCaseAndUser(legalCase, user)) {
            throw new UnauthorizedException("You do not have access to this case. Only case members can view case details.");
        }
    }

    private void verifyCaseModification(LegalCase legalCase, String userIdentifier) {
        User user = findUserByIdentifier(userIdentifier);

        // Allow owner automatically
        if (legalCase.getOwner().getId().equals(user.getId())) {
            return;
        }

        if (!caseMemberRepository.existsByLegalCaseAndUser(legalCase, user)) {
            throw new UnauthorizedException("You do not have access to this case.");
        }

        boolean isCaseLawyer = caseMemberRepository.findByLegalCaseAndUser(legalCase, user)
                .map(member -> member.getRole() == CaseMemberRole.LAWYER)
                .orElse(false);

        if (!isCaseLawyer && !user.isAdmin()) {
            throw new UnauthorizedException("Only case lawyers can modify case settings");
        }
    }

    private void verifyAdminAccess(String userIdentifier) {
        User user = findUserByIdentifier(userIdentifier);
        if (!user.isAdmin()) {
            throw new UnauthorizedException("Admin access required");
        }
    }

    private List<LegalCase> filterCasesByMembership(List<LegalCase> cases, String userIdentifier) {
        Long userId = getUserIdFromIdentifier(userIdentifier);
        Set<Long> userCaseIds = caseRepository.findCasesByMemberId(userId)
                .stream()
                .map(LegalCase::getId)
                .collect(Collectors.toSet());

        return cases.stream()
                .filter(c -> userCaseIds.contains(c.getId()))
                .collect(Collectors.toList());
    }

    private void verifyCaseNotDeleted(LegalCase legalCase) {
        if (legalCase.isDeleted()) {
            throw new InvalidStatusTransitionException("Cannot perform actions on a deleted case");
        }
    }

    // ============================================
    // CREATE
    // ============================================

    @Transactional
    public LegalCase createCase(String title, String description, CaseType type,
                                CasePriority priority, LocalDate dueDate,
                                String ownerIdentifier, Set<String> assignedUserIdentifiers) {
        log.info("Creating new case: {}", title);

        User owner = findUserByIdentifier(ownerIdentifier);
        log.info("Owner found: {} with role: {}", owner.getUsername(), owner.getRole());

        LegalCase legalCase = new LegalCase();
        legalCase.setCaseNumber(generateCaseNumber());
        legalCase.setTitle(title);
        legalCase.setDescription(description);
        legalCase.setType(type);
        legalCase.setPriority(priority != null ? priority : CasePriority.MEDIUM);
        legalCase.setDueDate(dueDate);
        legalCase.setStatus(CaseStatus.OPEN);
        legalCase.setOwner(owner);
        legalCase.setFilingDate(LocalDate.now());
        legalCase.setLocked(false);

        LegalCase saved = caseRepository.save(legalCase);
        log.info("Case created with number: {}, owner: {}", saved.getCaseNumber(), saved.getOwner().getUsername());

        addMemberToCaseByIdentifier(saved.getCaseNumber(), owner.getUsername(), CaseMemberRole.LAWYER, owner.getUsername());
        log.info("Owner added as lawyer to case: {}", saved.getCaseNumber());

        if (assignedUserIdentifiers != null && !assignedUserIdentifiers.isEmpty()) {
            for (String userIdentifier : assignedUserIdentifiers) {
                try {
                    User user = findUserByIdentifier(userIdentifier);
                    addMemberToCaseByIdentifier(saved.getCaseNumber(), user.getUsername(), CaseMemberRole.STAFF, owner.getUsername());
                } catch (ResourceNotFoundException e) {
                    log.warn("User not found: {}, skipping", userIdentifier);
                }
            }
        }

        recordAudit(AuditAction.CASE_CREATE, EntityType.CASE,
                saved.getId(), saved.getCaseNumber(),
                null, saved,
                "Title: " + title,
                true, null);

        return saved;
    }

    // ============================================
    // READ (Single)
    // ============================================

    public LegalCase getCase(String caseIdentifier, String userIdentifier) {
        LegalCase legalCase = findCase(caseIdentifier);
        verifyCaseAccess(legalCase, userIdentifier);
        verifyCaseNotDeleted(legalCase);
        return legalCase;
    }

    // ============================================
    // READ (Lists)
    // ============================================

    public List<LegalCase> getMyCases(String userIdentifier) {
        Long userId = getUserIdFromIdentifier(userIdentifier);
        List<LegalCase> ownedCases = caseRepository.findByOwnerIdWithDetails(userId);
        List<LegalCase> memberCases = caseRepository.findCasesByMemberIdWithDetails(userId);

        // Filter out deleted cases
        List<LegalCase> activeCases = new ArrayList<>();
        activeCases.addAll(ownedCases.stream().filter(c -> !c.isDeleted()).collect(Collectors.toList()));
        activeCases.addAll(memberCases.stream().filter(c -> !c.isDeleted()).collect(Collectors.toList()));
        return activeCases.stream().distinct().collect(Collectors.toList());
    }

    public Page<LegalCase> getMyCasesPaginated(String userIdentifier, int page, int size) {
        Long userId = getUserIdFromIdentifier(userIdentifier);
        Pageable pageable = PageRequest.of(page, size);
        return caseRepository.findMyCasesPaged(userId, pageable);
    }

    public List<LegalCase> getLockedCases(String userIdentifier) {
        verifyAdminAccess(userIdentifier);
        List<LegalCase> cases = caseRepository.findByIsLockedWithDetails(true);
        return cases.stream().filter(c -> !c.isDeleted()).collect(Collectors.toList());
    }

    public Page<LegalCase> getAllCasesPaginated(String userIdentifier, int page, int size) {
        verifyAdminAccess(userIdentifier);
        Pageable pageable = PageRequest.of(page, size);
        return caseRepository.findAllPaged(pageable);
    }

    // ============================================
    // SEARCH METHODS
    // ============================================

    public List<LegalCase> searchUserCases(String searchTerm, String userIdentifier) {
        if (searchTerm == null || searchTerm.trim().isEmpty()) {
            return List.of();
        }
        Long userId = getUserIdFromIdentifier(userIdentifier);
        return caseRepository.searchUserCases(userId, searchTerm.trim());
    }

    public List<LegalCase> getCasesByStatus(CaseStatus status, String userIdentifier) {
        List<LegalCase> allCases = caseRepository.findByStatusWithDetails(status);
        return filterCasesByMembership(allCases, userIdentifier);
    }

    public List<LegalCase> getCasesByPriority(CasePriority priority, String userIdentifier) {
        List<LegalCase> allCases = caseRepository.findByPriorityWithDetails(priority);
        return filterCasesByMembership(allCases, userIdentifier);
    }

    public List<LegalCase> getCasesByType(CaseType type, String userIdentifier) {
        List<LegalCase> allCases = caseRepository.findByTypeWithDetails(type);
        return filterCasesByMembership(allCases, userIdentifier);
    }

    public List<LegalCase> getCasesByAssignedUser(String targetUserIdentifier, String requestingUserIdentifier) {
        User targetUser = findUserByIdentifier(targetUserIdentifier);
        User requestingUser = findUserByIdentifier(requestingUserIdentifier);

        if (!targetUser.getId().equals(requestingUser.getId()) && !requestingUser.isAdmin()) {
            throw new UnauthorizedException("You can only view cases assigned to yourself");
        }

        return caseRepository.findCasesByMemberIdWithDetails(targetUser.getId());
    }

    public List<LegalCase> getCasesByCreator(String creatorIdentifier, String requestingUserIdentifier) {
        User creator = findUserByIdentifier(creatorIdentifier);
        findUserByIdentifier(requestingUserIdentifier);
        return caseRepository.findByOwnerIdWithDetails(creator.getId());
    }

    // ============================================
    // DUE DATE FILTER METHODS
    // ============================================

    public List<LegalCase> getCasesDueBefore(String userIdentifier, LocalDate date) {
        findUserByIdentifier(userIdentifier);
        List<LegalCase> allCases = caseRepository.findByDueDateBeforeWithDetails(date);
        return filterCasesByMembership(allCases, userIdentifier);
    }

    public List<LegalCase> getCasesDueAfter(String userIdentifier, LocalDate date) {
        findUserByIdentifier(userIdentifier);
        List<LegalCase> allCases = caseRepository.findByDueDateAfterWithDetails(date);
        return filterCasesByMembership(allCases, userIdentifier);
    }

    public List<LegalCase> getCasesDueBetween(String userIdentifier, LocalDate start, LocalDate end) {
        findUserByIdentifier(userIdentifier);
        List<LegalCase> allCases = caseRepository.findByDueDateBetweenWithDetails(start, end);
        return filterCasesByMembership(allCases, userIdentifier);
    }

    // ============================================
    // STATISTICS
    // ============================================

    public Map<String, Long> getCaseStatistics(String userIdentifier) {
        User user = findUserByIdentifier(userIdentifier);

        Map<String, Long> stats = new HashMap<>();

        if (user.isAdmin()) {
            stats.put("totalCases", caseRepository.count());
            stats.put("openCases", caseRepository.countByStatus(CaseStatus.OPEN));
            stats.put("inProgressCases", caseRepository.countByStatus(CaseStatus.IN_PROGRESS));
            stats.put("closedCases", caseRepository.countByStatus(CaseStatus.CLOSED));
            stats.put("archivedCases", caseRepository.countByStatus(CaseStatus.ARCHIVED));
            stats.put("highPriorityCases", caseRepository.countByPriority(CasePriority.HIGH));
            stats.put("urgentPriorityCases", caseRepository.countByPriority(CasePriority.URGENT));
            stats.put("lockedCases", (long) caseRepository.findByIsLockedWithDetails(true).size());
        } else {
            List<LegalCase> userCases = getMyCases(userIdentifier);
            stats.put("totalCases", (long) userCases.size());
            stats.put("openCases", userCases.stream().filter(c -> c.getStatus() == CaseStatus.OPEN).count());
            stats.put("inProgressCases", userCases.stream().filter(c -> c.getStatus() == CaseStatus.IN_PROGRESS).count());
            stats.put("closedCases", userCases.stream().filter(c -> c.getStatus() == CaseStatus.CLOSED).count());
            stats.put("archivedCases", userCases.stream().filter(c -> c.getStatus() == CaseStatus.ARCHIVED).count());
            stats.put("highPriorityCases", userCases.stream().filter(c -> c.getPriority() == CasePriority.HIGH).count());
            stats.put("urgentPriorityCases", userCases.stream().filter(c -> c.getPriority() == CasePriority.URGENT).count());
        }

        return stats;
    }

    // ============================================
    // UPDATE METHODS
    // ============================================

    @Transactional
    public LegalCase updateStatus(String caseIdentifier, CaseStatus newStatus, String userIdentifier) {
        log.info("User {} attempting to update case {} status to {}", userIdentifier, caseIdentifier, newStatus);

        LegalCase legalCase = findCase(caseIdentifier);
        verifyCaseAccess(legalCase, userIdentifier);
        verifyCaseNotDeleted(legalCase);
        verifyCaseModification(legalCase, userIdentifier);

        if (!legalCase.canTransitionTo(newStatus)) {
            throw new InvalidStatusTransitionException(String.format(
                    "Invalid status transition from %s to %s",
                    legalCase.getStatus(), newStatus));
        }

        validateTransitionConditions(legalCase, newStatus);

        CaseStatus oldStatus = legalCase.getStatus();
        legalCase.setStatus(newStatus);

        if (newStatus == CaseStatus.CLOSED) {
            legalCase.setLocked(true);
            log.info("Case {} automatically locked upon closing", legalCase.getCaseNumber());
        }

        caseRepository.save(legalCase);
        log.info("Case {} status updated to {}", legalCase.getCaseNumber(), newStatus);

        recordAudit(AuditAction.CASE_STATUS_CHANGE, EntityType.CASE,
                legalCase.getId(), legalCase.getCaseNumber(),
                oldStatus, newStatus,
                "Status changed from " + oldStatus + " to " + newStatus,
                true, null);

        return legalCase;
    }

    @Transactional
    public LegalCase updatePriority(String caseIdentifier, CasePriority priority, String userIdentifier) {
        LegalCase legalCase = findCase(caseIdentifier);
        verifyCaseAccess(legalCase, userIdentifier);
        verifyCaseNotDeleted(legalCase);
        verifyCaseModification(legalCase, userIdentifier);

        CasePriority oldPriority = legalCase.getPriority();
        legalCase.setPriority(priority);
        caseRepository.save(legalCase);

        recordAudit(AuditAction.CASE_PRIORITY_CHANGE, EntityType.CASE,
                legalCase.getId(), legalCase.getCaseNumber(),
                oldPriority, priority,
                "Priority changed from " + oldPriority + " to " + priority,
                true, null);

        return legalCase;
    }

    @Transactional
    public LegalCase updateDueDate(String caseIdentifier, LocalDate dueDate, String userIdentifier) {
        LegalCase legalCase = findCase(caseIdentifier);
        verifyCaseAccess(legalCase, userIdentifier);
        verifyCaseNotDeleted(legalCase);
        verifyCaseModification(legalCase, userIdentifier);

        LocalDate oldDueDate = legalCase.getDueDate();
        legalCase.setDueDate(dueDate);
        caseRepository.save(legalCase);

        recordAudit(AuditAction.CASE_UPDATE, EntityType.CASE,
                legalCase.getId(), legalCase.getCaseNumber(),
                oldDueDate, dueDate,
                "Due date changed from " + oldDueDate + " to " + dueDate,
                true, null);

        return legalCase;
    }

    @Transactional
    public LegalCase setLocked(String caseIdentifier, boolean locked, String userIdentifier) {
        log.info("User {} setting case {} locked status to {}", userIdentifier, caseIdentifier, locked);

        LegalCase legalCase = findCase(caseIdentifier);
        User user = findUserByIdentifier(userIdentifier);

        if (!user.isAdmin()) {
            throw new UnauthorizedException("Only admins can lock/unlock cases");
        }

        verifyCaseNotDeleted(legalCase);

        boolean oldLocked = legalCase.isLocked();
        legalCase.setLocked(locked);
        caseRepository.save(legalCase);

        recordAudit(locked ? AuditAction.CASE_LOCK : AuditAction.CASE_UNLOCK, EntityType.CASE,
                legalCase.getId(), legalCase.getCaseNumber(),
                oldLocked, locked,
                "Lock status changed from " + oldLocked + " to " + locked,
                true, null);

        return legalCase;
    }

    // ============================================
    // MEMBER MANAGEMENT
    // ============================================

    @Transactional
    public CaseMember addMemberToCaseByIdentifier(String caseIdentifier, String userIdentifierToAdd, CaseMemberRole role, String addedByUserIdentifier) {
        log.info("Adding user with identifier '{}' to case {} with role {} by user {}",
                userIdentifierToAdd, caseIdentifier, role, addedByUserIdentifier);

        LegalCase legalCase = findCase(caseIdentifier);
        verifyCaseModification(legalCase, addedByUserIdentifier);
        verifyCaseNotDeleted(legalCase);

        if (legalCase.isLocked()) {
            throw new InvalidStatusTransitionException("Cannot add members to a locked case");
        }

        User userToAdd = findUserByIdentifier(userIdentifierToAdd);

        if (caseMemberRepository.existsByLegalCaseAndUser(legalCase, userToAdd)) {
            throw new DuplicateResourceException("User is already a member of this case");
        }

        CaseMemberRole actualRole = role != null ? role : CaseMemberRole.STAFF;

        CaseMember caseMember = new CaseMember();
        caseMember.setLegalCase(legalCase);
        caseMember.setUser(userToAdd);
        caseMember.setRole(actualRole);

        User addedBy = findUserByIdentifier(addedByUserIdentifier);
        notificationService.notifyUserAddedToCase(userToAdd.getId(), legalCase.getId(), addedBy.getId());

        log.info("User '{}' (ID: {}) added to case {} as {}", userIdentifierToAdd, userToAdd.getId(), legalCase.getCaseNumber(), actualRole);

        CaseMember saved = caseMemberRepository.save(caseMember);

        recordAudit(AuditAction.CASE_MEMBER_ADD, EntityType.CASE_MEMBER,
                saved.getId(), null,
                null, saved,
                "User " + userToAdd.getUsername() + " added as " + actualRole + " to case " + legalCase.getCaseNumber(),
                true, null);

        return saved;
    }

    @Transactional
    public List<CaseMember> addMembersToCaseByIdentifiers(String caseIdentifier, List<String> userIdentifiersToAdd, String addedByUserIdentifier) {
        log.info("Adding {} users to case {}", userIdentifiersToAdd.size(), caseIdentifier);

        LegalCase legalCase = findCase(caseIdentifier);
        verifyCaseModification(legalCase, addedByUserIdentifier);
        verifyCaseNotDeleted(legalCase);

        if (legalCase.isLocked()) {
            throw new InvalidStatusTransitionException("Cannot add members to a locked case");
        }

        User addedBy = findUserByIdentifier(addedByUserIdentifier);
        List<CaseMember> addedMembers = new ArrayList<>();
        List<String> notFoundUsers = new ArrayList<>();
        List<String> alreadyMembers = new ArrayList<>();

        boolean isOwner = legalCase.getOwner().getId().equals(addedBy.getId());
        boolean isLawyer = caseMemberRepository.isLawyerInCase(legalCase.getId(), addedBy.getId());

        if (!isOwner && !isLawyer) {
            throw new AccessDeniedException("Only case owners and case lawyers can add members to this case");
        }

        for (String identifier : userIdentifiersToAdd) {
            try {
                User userToAdd = findUserByIdentifier(identifier);

                if (caseMemberRepository.existsByLegalCaseAndUser(legalCase, userToAdd)) {
                    alreadyMembers.add(identifier);
                    continue;
                }

                CaseMember caseMember = new CaseMember();
                caseMember.setLegalCase(legalCase);
                caseMember.setUser(userToAdd);
                caseMember.setRole(CaseMemberRole.STAFF);

                CaseMember saved = caseMemberRepository.save(caseMember);
                addedMembers.add(saved);

                notificationService.notifyUserAddedToCase(userToAdd.getId(), legalCase.getId(), addedBy.getId());

                log.info("User {} added to case {} as STAFF", userToAdd.getUsername(), legalCase.getCaseNumber());

                recordAudit(AuditAction.CASE_MEMBER_ADD, EntityType.CASE_MEMBER,
                        saved.getId(), null,
                        null, saved,
                        "User " + userToAdd.getUsername() + " added as STAFF to case " + legalCase.getCaseNumber() + " by " + addedBy.getUsername(),
                        true, null);

            } catch (ResourceNotFoundException e) {
                notFoundUsers.add(identifier);
                log.warn("User not found: {}", identifier);
            } catch (DuplicateResourceException e) {
                alreadyMembers.add(identifier);
                log.warn("User already member: {}", identifier);
            } catch (Exception e) {
                log.error("Unexpected error adding user {}: {}", identifier, e.getMessage());
            }
        }

        if (!notFoundUsers.isEmpty()) {
            log.warn("Users not found: {}", notFoundUsers);
        }
        if (!alreadyMembers.isEmpty()) {
            log.warn("Users already members: {}", alreadyMembers);
        }

        log.info("Successfully added {} members to case {}", addedMembers.size(), legalCase.getCaseNumber());
        return addedMembers;
    }

    public List<MemberResponse> getCaseMembers(String caseIdentifier, String userIdentifier) {
        LegalCase legalCase = findCase(caseIdentifier);
        verifyCaseAccess(legalCase, userIdentifier);
        verifyCaseNotDeleted(legalCase);

        List<CaseMember> members = caseMemberRepository.findByLegalCaseWithDetails(legalCase);
        return members.stream()
                .map(MemberResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional
    public void removeMemberFromCase(String caseIdentifier, String userIdentifierToRemove, String adminUserIdentifier) {
        log.info("Admin {} removing user {} from case {}", adminUserIdentifier, userIdentifierToRemove, caseIdentifier);

        LegalCase legalCase = findCase(caseIdentifier);
        verifyCaseModification(legalCase, adminUserIdentifier);
        verifyCaseNotDeleted(legalCase);

        if (legalCase.isLocked()) {
            throw new InvalidStatusTransitionException("Cannot remove members from a locked case");
        }

        User userToRemove = findUserByIdentifier(userIdentifierToRemove);

        if (legalCase.getOwner().getId().equals(userToRemove.getId())) {
            throw new InvalidStatusTransitionException("Cannot remove the case owner");
        }

        User adminUser = findUserByIdentifier(adminUserIdentifier);

        notificationService.notifyUserRemovedFromCase(userToRemove.getId(), legalCase.getId(), adminUser.getId());

        caseMemberRepository.deleteByLegalCaseAndUser(legalCase, userToRemove);

        recordAudit(AuditAction.CASE_MEMBER_REMOVE, EntityType.CASE_MEMBER,
                null, null,
                null, null,
                "User " + userToRemove.getUsername() + " removed from case " + legalCase.getCaseNumber() + " by admin " + adminUser.getUsername(),
                true, null);
    }

    // ============================================
    // PROGRESS & UTILITY METHODS
    // ============================================

    public int getCaseProgressPercentage(String caseIdentifier, String userIdentifier) {
        LegalCase legalCase = findCase(caseIdentifier);
        verifyCaseAccess(legalCase, userIdentifier);
        verifyCaseNotDeleted(legalCase);

        long totalMandatory = caseRepository.countMandatoryTasksByCaseId(legalCase.getId());
        if (totalMandatory == 0) {
            return 0;
        }
        long completedMandatory = caseRepository.countCompletedMandatoryTasksByCaseId(legalCase.getId());
        return (int) ((completedMandatory * 100) / totalMandatory);
    }

    public boolean isReadyForInProgress(String caseIdentifier, String userIdentifier) {
        LegalCase legalCase = findCase(caseIdentifier);
        verifyCaseAccess(legalCase, userIdentifier);
        verifyCaseNotDeleted(legalCase);
        return caseRepository.countMandatoryTasksByCaseId(legalCase.getId()) > 0;
    }

    public boolean isReadyForClosed(String caseIdentifier, String userIdentifier) {
        LegalCase legalCase = findCase(caseIdentifier);
        verifyCaseAccess(legalCase, userIdentifier);
        verifyCaseNotDeleted(legalCase);

        long totalMandatory = caseRepository.countMandatoryTasksByCaseId(legalCase.getId());
        if (totalMandatory == 0) {
            return false;
        }
        long completedMandatory = caseRepository.countCompletedMandatoryTasksByCaseId(legalCase.getId());
        return completedMandatory == totalMandatory;
    }

    public boolean isCaseMemberWithRole(String caseIdentifier, String userIdentifier, CaseMemberRole requiredRole) {
        try {
            LegalCase legalCase = findCase(caseIdentifier);
            User user = findUserByIdentifier(userIdentifier);
            return caseMemberRepository.findByLegalCaseAndUser(legalCase, user)
                    .map(member -> member.getRole() == requiredRole)
                    .orElse(false);
        } catch (Exception e) {
            return false;
        }
    }

    // ============================================
    // DELETE METHODS
    // ============================================

    @Transactional
    public void softDeleteCase(String caseIdentifier, String userIdentifier) {
        log.info("User {} attempting to soft delete case: {}", userIdentifier, caseIdentifier);

        LegalCase legalCase = findCase(caseIdentifier);
        User user = findUserByIdentifier(userIdentifier);

        // Check if already deleted
        if (legalCase.isDeleted()) {
            throw new InvalidStatusTransitionException("Case is already deleted");
        }

        boolean isCreator = legalCase.getOwner().getId().equals(user.getId());
        boolean isCaseLawyer = caseMemberRepository.isLawyerInCase(legalCase.getId(), user.getId());

        if (!isCreator && !isCaseLawyer && !user.isAdmin()) {
            throw new UnauthorizedException("Only case creator, case lawyers, or admins can delete this case");
        }

        caseRepository.softDeleteById(legalCase.getId());

        log.info("Case {} soft deleted successfully", legalCase.getCaseNumber());

        recordAudit(AuditAction.CASE_DELETE, EntityType.CASE,
                legalCase.getId(), legalCase.getCaseNumber(),
                legalCase, null,
                "Case soft deleted by " + user.getUsername(),
                true, null);
    }

    @Transactional
    public void restoreCase(String caseIdentifier, String userIdentifier) {
        log.info("User {} attempting to restore case: {}", userIdentifier, caseIdentifier);

        verifyAdminAccess(userIdentifier);

        LegalCase legalCase = findCase(caseIdentifier);

        // Check if it's actually deleted
        if (!legalCase.isDeleted()) {
            throw new InvalidStatusTransitionException("Case is not deleted");
        }

        caseRepository.restoreById(legalCase.getId());
        log.info("Case {} restored successfully", legalCase.getCaseNumber());

        recordAudit(AuditAction.CASE_RESTORE, EntityType.CASE,
                legalCase.getId(), legalCase.getCaseNumber(),
                null, legalCase,
                "Case restored by admin " + userIdentifier,
                true, null);
    }

    public List<LegalCase> getDeletedCases(String userIdentifier) {
        verifyAdminAccess(userIdentifier);
        return caseRepository.findByIsDeletedTrueWithDetails();
    }

    // ============================================
    // TRANSITION VALIDATION (Private helpers)
    // ============================================

    private void validateTransitionConditions(LegalCase legalCase, CaseStatus newStatus) {
        switch (newStatus) {
            case IN_PROGRESS:
                validateInProgressTransition(legalCase);
                break;
            case CLOSED:
                validateClosedTransition(legalCase);
                break;
            case ARCHIVED:
                validateArchivedTransition(legalCase);
                break;
            default:
                break;
        }
    }

    private void validateInProgressTransition(LegalCase legalCase) {
        long mandatoryTaskCount = caseRepository.countMandatoryTasksByCaseId(legalCase.getId());
        if (mandatoryTaskCount == 0) {
            throw new InvalidStatusTransitionException("Cannot move to IN_PROGRESS: At least one task must be assigned to the case");
        }
        log.info("Case {} has {} mandatory tasks - ready for IN_PROGRESS",
                legalCase.getCaseNumber(), mandatoryTaskCount);
    }

    private void validateClosedTransition(LegalCase legalCase) {
        long totalMandatory = caseRepository.countMandatoryTasksByCaseId(legalCase.getId());
        long completedMandatory = caseRepository.countCompletedMandatoryTasksByCaseId(legalCase.getId());

        if (totalMandatory == 0) {
            throw new InvalidStatusTransitionException("Cannot close case: No mandatory tasks defined");
        }

        if (completedMandatory < totalMandatory) {
            throw new InvalidStatusTransitionException(String.format(
                    "Cannot close case: Only %d of %d mandatory tasks completed",
                    completedMandatory, totalMandatory));
        }

        log.info("Case {} has all {} mandatory tasks completed - ready for CLOSED",
                legalCase.getCaseNumber(), totalMandatory);
    }

    private void validateArchivedTransition(LegalCase legalCase) {
        if (legalCase.getStatus() != CaseStatus.CLOSED) {
            throw new InvalidStatusTransitionException("Cannot archive case that is not CLOSED");
        }
        if (!legalCase.isLocked()) {
            throw new InvalidStatusTransitionException("Cannot archive unlocked case. Case must be locked first.");
        }
    }

    private String generateCaseNumber() {
        int currentYear = Year.now().getValue();
        long nextNumber = caseRepository.count() + 1;
        String caseNumber = String.format("CASE-%d-%05d", currentYear, nextNumber);

        while (caseRepository.existsByCaseNumber(caseNumber)) {
            nextNumber++;
            caseNumber = String.format("CASE-%d-%05d", currentYear, nextNumber);
        }
        return caseNumber;
    }

    // NEW: Promote case member to LAWYER
    @Transactional
    public CaseMember promoteMemberToLawyer(String caseIdentifier, String userIdentifierToPromote, String promotedByUserIdentifier) {
        log.info("User {} promoting {} to lawyer in case {}",
                promotedByUserIdentifier, userIdentifierToPromote, caseIdentifier);

        LegalCase legalCase = findCase(caseIdentifier);
        User promoter = findUserByIdentifier(promotedByUserIdentifier);
        User userToPromote = findUserByIdentifier(userIdentifierToPromote);

        verifyCaseNotDeleted(legalCase);

        boolean isOwner = legalCase.getOwner().getId().equals(promoter.getId());
        boolean isLawyer = caseMemberRepository.isLawyerInCase(legalCase.getId(), promoter.getId());

        if (!isOwner && !isLawyer) {
            throw new AccessDeniedException("Only case owners and case lawyers can promote members to lawyer");
        }

        if (legalCase.isLocked()) {
            throw new InvalidStatusTransitionException("Cannot promote members in a locked case");
        }

        CaseMember caseMember = caseMemberRepository.findByLegalCaseAndUser(legalCase, userToPromote)
                .orElseThrow(() -> new ResourceNotFoundException("User is not a member of this case"));

        if (caseMember.getRole() == CaseMemberRole.LAWYER) {
            throw new DuplicateResourceException("User is already a lawyer in this case");
        }

        caseMember.setRole(CaseMemberRole.LAWYER);
        CaseMember saved = caseMemberRepository.save(caseMember);

        recordAudit(AuditAction.CASE_MEMBER_ADD, EntityType.CASE_MEMBER,
                saved.getId(), null,
                null, saved,
                "User " + userToPromote.getUsername() + " promoted to LAWYER by " + promoter.getUsername(),
                true, null);

        log.info("User {} promoted to lawyer in case {}", userIdentifierToPromote, caseIdentifier);
        return saved;
    }

    // NEW: Demote case lawyer to STAFF (Owner only)
    @Transactional
    public CaseMember demoteLawyerToStaff(String caseIdentifier, String userIdentifierToDemote, String demotedByUserIdentifier) {
        log.info("User {} demoting {} from lawyer to staff in case {}",
                demotedByUserIdentifier, userIdentifierToDemote, caseIdentifier);

        LegalCase legalCase = findCase(caseIdentifier);
        User demoter = findUserByIdentifier(demotedByUserIdentifier);
        User userToDemote = findUserByIdentifier(userIdentifierToDemote);

        verifyCaseNotDeleted(legalCase);

        if (!legalCase.getOwner().getId().equals(demoter.getId())) {
            throw new AccessDeniedException("Only the case owner can demote lawyers to staff");
        }

        if (legalCase.isLocked()) {
            throw new InvalidStatusTransitionException("Cannot demote members in a locked case");
        }

        if (legalCase.getOwner().getId().equals(userToDemote.getId())) {
            throw new InvalidStatusTransitionException("Cannot demote the case owner");
        }

        CaseMember caseMember = caseMemberRepository.findByLegalCaseAndUser(legalCase, userToDemote)
                .orElseThrow(() -> new ResourceNotFoundException("User is not a member of this case"));

        if (caseMember.getRole() != CaseMemberRole.LAWYER) {
            throw new BusinessException("User is not a lawyer in this case");
        }

        caseMember.setRole(CaseMemberRole.STAFF);
        CaseMember saved = caseMemberRepository.save(caseMember);

        recordAudit(AuditAction.CASE_MEMBER_REMOVE, EntityType.CASE_MEMBER,
                saved.getId(), null,
                null, saved,
                "User " + userToDemote.getUsername() + " demoted to STAFF by owner " + demoter.getUsername(),
                true, null);

        log.info("User {} demoted to staff in case {}", userIdentifierToDemote, caseIdentifier);
        return saved;
    }
}