package com.legalcase.service;

import com.legalcase.exception.DuplicateResourceException;
import com.legalcase.exception.InvalidStatusTransitionException;
import com.legalcase.exception.ResourceNotFoundException;
import com.legalcase.exception.UnauthorizedException;
import com.legalcase.repository.NotificationRepository;
import com.legalcase.dto.response.MemberResponse;
import com.legalcase.entity.CaseMember;
import com.legalcase.entity.LegalCase;
import com.legalcase.entity.User;
import com.legalcase.enums.*;
import com.legalcase.repository.CaseMemberRepository;
import com.legalcase.repository.CaseRepository;
import com.legalcase.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
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

    @Transactional
    public LegalCase createCase(String title, String description, CaseType type,
                                CasePriority priority, LocalDate dueDate,
                                Long ownerId, Set<Long> assignedUserIds) {
        log.info("Creating new case: {}", title);

        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + ownerId));

        if (!owner.isLawyer() && !owner.isAdmin()) {
            throw new UnauthorizedException("Only lawyers and admins can create cases");
        }

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

        addMemberToCaseByIdentifier(saved.getId(), owner.getUsername(), CaseMemberRole.LAWYER, ownerId);

        if (assignedUserIds != null && !assignedUserIds.isEmpty()) {
            for (Long userId : assignedUserIds) {
                User user = userRepository.findById(userId).orElse(null);
                if (user != null) {
                    addMemberToCaseByIdentifier(saved.getId(), user.getUsername(), CaseMemberRole.STAFF, ownerId);
                }
            }
        }

        log.info("Case created with number: {}", saved.getCaseNumber());
        return findById(saved.getId());
    }

    public LegalCase findById(Long id) {
        return caseRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Case not found with ID: " + id));
    }

    public List<LegalCase> getCasesByOwner(Long ownerId) {
        return caseRepository.findByOwnerIdWithDetails(ownerId);
    }

    public List<LegalCase> getCasesByMember(Long userId) {
        return caseRepository.findCasesByMemberIdWithDetails(userId);
    }

    public List<LegalCase> getCasesByStatus(CaseStatus status) {
        return caseRepository.findByStatusWithDetails(status);
    }

    @Transactional
    public LegalCase updateStatus(Long caseId, CaseStatus newStatus, Long userId) {
        log.info("User {} attempting to update case {} status to {}", userId, caseId, newStatus);

        LegalCase legalCase = findById(caseId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!user.isLawyer() && !user.isAdmin()) {
            throw new UnauthorizedException("Only lawyers and admins can change case status");
        }

        if (!legalCase.canTransitionTo(newStatus)) {
            throw new InvalidStatusTransitionException(String.format(
                    "Invalid status transition from %s to %s",
                    legalCase.getStatus(), newStatus));
        }

        validateTransitionConditions(legalCase, newStatus);

        legalCase.setStatus(newStatus);

        if (newStatus == CaseStatus.CLOSED) {
            legalCase.setLocked(true);
            log.info("Case {} automatically locked upon closing", caseId);
        }

        caseRepository.save(legalCase);
        log.info("Case {} status updated to {}", caseId, newStatus);

        return findById(caseId);
    }

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
                legalCase.getId(), mandatoryTaskCount);
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
                legalCase.getId(), totalMandatory);
    }

    private void validateArchivedTransition(LegalCase legalCase) {
        if (legalCase.getStatus() != CaseStatus.CLOSED) {
            throw new InvalidStatusTransitionException("Cannot archive case that is not CLOSED");
        }
        if (!legalCase.isLocked()) {
            throw new InvalidStatusTransitionException("Cannot archive unlocked case. Case must be locked first.");
        }
    }

    @Transactional
    public LegalCase setLocked(Long caseId, boolean locked, Long userId) {
        log.info("User {} setting case {} locked status to {}", userId, caseId, locked);

        LegalCase legalCase = findById(caseId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!user.isAdmin()) {
            throw new UnauthorizedException("Only admins can lock/unlock cases");
        }

        legalCase.setLocked(locked);
        caseRepository.save(legalCase);
        return findById(caseId);
    }

    @Transactional
    public LegalCase updatePriority(Long caseId, CasePriority priority, Long userId) {
        LegalCase legalCase = findById(caseId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!user.isLawyer() && !user.isAdmin()) {
            throw new UnauthorizedException("Only lawyers and admins can change case priority");
        }

        legalCase.setPriority(priority);
        caseRepository.save(legalCase);
        return findById(caseId);
    }

    @Transactional
    public LegalCase updateDueDate(Long caseId, LocalDate dueDate, Long userId) {
        LegalCase legalCase = findById(caseId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!user.isLawyer() && !user.isAdmin()) {
            throw new UnauthorizedException("Only lawyers and admins can change case due date");
        }

        legalCase.setDueDate(dueDate);
        caseRepository.save(legalCase);
        return findById(caseId);
    }

    /**
     * Add member to case by user ID (internal use).
     */
    @Transactional
    public CaseMember addMemberToCaseById(Long caseId, Long userId, CaseMemberRole role) {
        log.info("Adding user ID {} to case {} with role {}", userId, caseId, role);

        LegalCase legalCase = findById(caseId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        if (legalCase.isLocked()) {
            throw new InvalidStatusTransitionException("Cannot add members to a locked case");
        }

        if (caseMemberRepository.existsByLegalCaseAndUser(legalCase, user)) {
            throw new DuplicateResourceException("User is already a member of this case");
        }

        validateUserRoleForCaseRole(user, role);

        CaseMember caseMember = new CaseMember();
        caseMember.setLegalCase(legalCase);
        caseMember.setUser(user);
        caseMember.setRole(role);

        return caseMemberRepository.save(caseMember);
    }

    /**
     * Add member to case by identifier (username or email).
     * This is the MAIN method to use for public API.
     */
    @Transactional
    public CaseMember addMemberToCaseByIdentifier(Long caseId, String identifier, CaseMemberRole role, Long addedByUserId) {
        log.info("Adding user with identifier '{}' to case {} with role {} by user {}",
                identifier, caseId, role, addedByUserId);

        LegalCase legalCase = findById(caseId);

        if (legalCase.isLocked()) {
            throw new InvalidStatusTransitionException("Cannot add members to a locked case");
        }

        User user = findUserByIdentifier(identifier);

        if (caseMemberRepository.existsByLegalCaseAndUser(legalCase, user)) {
            throw new DuplicateResourceException("User is already a member of this case");
        }

        validateUserRoleForCaseRole(user, role);

        CaseMember caseMember = new CaseMember();
        caseMember.setLegalCase(legalCase);
        caseMember.setUser(user);
        caseMember.setRole(role);

        notificationService.notifyUserAddedToCase(user.getId(), caseId, addedByUserId);

        log.info("User '{}' (ID: {}) added to case {} as {}", identifier, user.getId(), caseId, role);

        return caseMemberRepository.save(caseMember);
    }

    /**
     * Add multiple members to case by identifiers (username or email).
     */
    @Transactional
    public List<CaseMember> addMembersToCaseByIdentifiers(Long caseId, List<String> identifiers, CaseMemberRole role, Long addedByUserId) {
        log.info("Adding {} users to case {} with role {}", identifiers.size(), caseId, role);

        LegalCase legalCase = findById(caseId);

        if (legalCase.isLocked()) {
            throw new InvalidStatusTransitionException("Cannot add members to a locked case");
        }

        List<CaseMember> addedMembers = new ArrayList<>();
        List<String> notFoundUsers = new ArrayList<>();
        List<String> alreadyMembers = new ArrayList<>();

        for (String identifier : identifiers) {
            try {
                // Find user by identifier (username or email)
                User user = findUserByIdentifier(identifier);  // This throws exception if not found

                if (caseMemberRepository.existsByLegalCaseAndUser(legalCase, user)) {
                    alreadyMembers.add(identifier);
                    continue;
                }

                validateUserRoleForCaseRole(user, role);

                CaseMember caseMember = new CaseMember();
                caseMember.setLegalCase(legalCase);
                caseMember.setUser(user);
                caseMember.setRole(role);

                CaseMember saved = caseMemberRepository.save(caseMember);
                addedMembers.add(saved);

                // Send notification
                notificationService.notifyUserAddedToCase(user.getId(), caseId, addedByUserId);

            } catch (ResourceNotFoundException e) {
                notFoundUsers.add(identifier);
                log.warn("User not found: {}", identifier);  // Use warn, not error
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

        log.info("Successfully added {} members to case {}", addedMembers.size(), caseId);
        return addedMembers;
    }

    /**
     * Find user by identifier (username or email).
     * Tries username first, then email.
     * Throws exception if not found.
     */
    private User findUserByIdentifier(String identifier) {
        return userRepository.findByUsername(identifier)
                .or(() -> userRepository.findByEmail(identifier))
                .orElseThrow(() -> new ResourceNotFoundException("User not found with username or email: " + identifier));
    }

    /**
     * Find user by identifier (username or email) - returns Optional.
     * Does NOT throw exception if not found.
     */
    private Optional<User> findUserByIdentifierOptional(String identifier) {
        return userRepository.findByUsername(identifier)
                .or(() -> userRepository.findByEmail(identifier));
    }

    private void validateUserRoleForCaseRole(User user, CaseMemberRole caseRole) {
        switch (caseRole) {
            case LAWYER:
                if (!user.isLawyer() && !user.isAdmin()) {
                    throw new InvalidStatusTransitionException("User '" + user.getUsername() +
                            "' cannot be assigned as LAWYER. User must have LAWYER or ADMIN system role.");
                }
                break;
            case STAFF:
                // Any authenticated user can be STAFF
                break;
        }
    }

    @Transactional
    public void removeMemberFromCase(Long caseId, Long userId, Long adminId) {
        log.info("Admin {} removing user {} from case {}", adminId, userId, caseId);

        LegalCase legalCase = findById(caseId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (legalCase.isLocked()) {
            throw new InvalidStatusTransitionException("Cannot remove members from a locked case");
        }

        if (legalCase.getOwner().getId().equals(userId)) {
            throw new InvalidStatusTransitionException("Cannot remove the case owner");
        }

        caseMemberRepository.deleteByLegalCaseAndUser(legalCase, user);
    }

    public List<MemberResponse> getCaseMembers(Long caseId) {
        LegalCase legalCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Case not found"));
        List<CaseMember> members = caseMemberRepository.findByLegalCaseWithDetails(legalCase);
        return members.stream()
                .map(MemberResponse::fromEntity)
                .collect(Collectors.toList());
    }

    public boolean isCaseMember(Long caseId, Long userId) {
        LegalCase legalCase = findById(caseId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return caseMemberRepository.existsByLegalCaseAndUser(legalCase, user);
    }

    public boolean isCaseMemberWithRole(Long caseId, Long userId, CaseMemberRole requiredRole) {
        try {
            LegalCase legalCase = findById(caseId);
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found"));
            return caseMemberRepository.findByLegalCaseAndUser(legalCase, user)
                    .map(member -> member.getRole() == requiredRole)
                    .orElse(false);
        } catch (Exception e) {
            return false;
        }
    }

    public int getCaseProgressPercentage(Long caseId) {
        long totalMandatory = caseRepository.countMandatoryTasksByCaseId(caseId);
        if (totalMandatory == 0) {
            return 0;
        }
        long completedMandatory = caseRepository.countCompletedMandatoryTasksByCaseId(caseId);
        return (int) ((completedMandatory * 100) / totalMandatory);
    }

    public boolean isReadyForInProgress(Long caseId) {
        return caseRepository.countMandatoryTasksByCaseId(caseId) > 0;
    }

    public boolean isReadyForClosed(Long caseId) {
        long totalMandatory = caseRepository.countMandatoryTasksByCaseId(caseId);
        if (totalMandatory == 0) {
            return false;
        }
        long completedMandatory = caseRepository.countCompletedMandatoryTasksByCaseId(caseId);
        return completedMandatory == totalMandatory;
    }

    private String generateCaseNumber() {
        int currentYear = Year.now().getValue();
        long nextNumber = caseRepository.count() + 1;
        return String.format("CASE-%d-%05d", currentYear, nextNumber);
    }
}


