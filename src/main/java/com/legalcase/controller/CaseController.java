package com.legalcase.controller;

import com.legalcase.dto.request.AddMemberRequest;
import com.legalcase.dto.request.AddMembersRequest;
import com.legalcase.dto.request.CreateCaseRequest;
import com.legalcase.dto.request.UpdateCaseStatusRequest;
import com.legalcase.dto.response.CaseResponse;
import com.legalcase.dto.response.MemberResponse;
import com.legalcase.entity.CaseMember;
import com.legalcase.entity.LegalCase;
import com.legalcase.enums.CaseMemberRole;
import com.legalcase.enums.CasePriority;
import com.legalcase.enums.CaseStatus;
import com.legalcase.enums.CaseType;
import com.legalcase.exception.InvalidStatusTransitionException;
import com.legalcase.security.JwtUtils;
import com.legalcase.service.CaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/cases")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Case Management", description = "Legal case management operations")
@SecurityRequirement(name = "Bearer Authentication")
public class CaseController {

    private final CaseService caseService;
    private final JwtUtils jwtUtils;

    // ============================================
    // CORE CRUD
    // ============================================

    @PostMapping
    public ResponseEntity<CaseResponse> createCase(
            @Valid @RequestBody CreateCaseRequest request,
            HttpServletRequest httpRequest) {

        String userIdentifier = extractUserIdentifier(httpRequest);

        LegalCase legalCase = caseService.createCase(
                request.getTitle(), request.getDescription(), request.getType(),
                request.getPriority(), request.getDueDate(), userIdentifier, request.getAssignedUserIdentifiers());

        List<MemberResponse> members = caseService.getCaseMembers(legalCase.getCaseNumber(), userIdentifier);
        CaseResponse response = buildCaseResponse(legalCase, members, userIdentifier);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{identifier}")
    public ResponseEntity<CaseResponse> getCase(
            @PathVariable String identifier,
            HttpServletRequest httpRequest) {

        String userIdentifier = extractUserIdentifier(httpRequest);
        LegalCase legalCase = caseService.getCase(identifier, userIdentifier);
        verifyCaseNotDeleted(legalCase);
        List<MemberResponse> members = caseService.getCaseMembers(identifier, userIdentifier);
        CaseResponse response = buildCaseResponse(legalCase, members, userIdentifier);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/my-cases")
    public ResponseEntity<Map<String, List<CaseResponse>>> getMyCases(HttpServletRequest httpRequest) {
        String userIdentifier = extractUserIdentifier(httpRequest);
        List<LegalCase> userCases = caseService.getMyCases(userIdentifier);

        // Filter out deleted cases
        List<LegalCase> activeCases = userCases.stream()
                .filter(c -> !c.isDeleted())
                .collect(Collectors.toList());

        List<CaseResponse> ownedCases = activeCases.stream()
                .filter(c -> c.getOwner().getUsername().equals(userIdentifier) || c.getOwner().getEmail().equals(userIdentifier))
                .map(c -> buildCaseResponse(c, caseService.getCaseMembers(c.getCaseNumber(), userIdentifier), userIdentifier))
                .collect(Collectors.toList());

        List<CaseResponse> memberCases = activeCases.stream()
                .filter(c -> !c.getOwner().getUsername().equals(userIdentifier) && !c.getOwner().getEmail().equals(userIdentifier))
                .map(c -> buildCaseResponse(c, caseService.getCaseMembers(c.getCaseNumber(), userIdentifier), userIdentifier))
                .collect(Collectors.toList());

        Map<String, List<CaseResponse>> response = new HashMap<>();
        response.put("createdByMe", ownedCases);
        response.put("assignedToMe", memberCases);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/my-cases/paginated")
    public ResponseEntity<Page<CaseResponse>> getMyCasesPaginated(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {

        String userIdentifier = extractUserIdentifier(request);
        Page<LegalCase> cases = caseService.getMyCasesPaginated(userIdentifier, page, size);
        Page<CaseResponse> responses = cases.map(c -> CaseResponse.fromEntity(c, List.of()));
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/locked")
    public ResponseEntity<List<CaseResponse>> getLockedCases(HttpServletRequest request) {
        String userIdentifier = extractUserIdentifier(request);
        List<LegalCase> cases = caseService.getLockedCases(userIdentifier);
        List<CaseResponse> responses = cases.stream()
                .filter(c -> !c.isDeleted())
                .map(c -> CaseResponse.fromEntity(c, List.of()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/admin/all")
    public ResponseEntity<Page<CaseResponse>> getAllCasesPaginated(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {

        String userIdentifier = extractUserIdentifier(request);
        Page<LegalCase> cases = caseService.getAllCasesPaginated(userIdentifier, page, size);
        Page<CaseResponse> responses = cases.map(c -> CaseResponse.fromEntity(c, List.of()));
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Long>> getCaseStatistics(HttpServletRequest request) {
        String userIdentifier = extractUserIdentifier(request);
        Map<String, Long> stats = caseService.getCaseStatistics(userIdentifier);
        return ResponseEntity.ok(stats);
    }

    // ============================================
    // UPDATE METHODS (with deleted case check)
    // ============================================

    @PatchMapping("/{identifier}/status")
    public ResponseEntity<CaseResponse> updateStatus(
            @PathVariable String identifier,
            @Valid @RequestBody UpdateCaseStatusRequest request,
            HttpServletRequest httpRequest) {

        String userIdentifier = extractUserIdentifier(httpRequest);
        LegalCase legalCase = caseService.getCase(identifier, userIdentifier);
        verifyCaseNotDeleted(legalCase);

        LegalCase updatedCase = caseService.updateStatus(identifier, request.getStatus(), userIdentifier);
        List<MemberResponse> members = caseService.getCaseMembers(identifier, userIdentifier);
        CaseResponse response = buildCaseResponse(updatedCase, members, userIdentifier);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{identifier}/priority")
    public ResponseEntity<CaseResponse> updatePriority(
            @PathVariable String identifier,
            @RequestParam CasePriority priority,
            HttpServletRequest httpRequest) {

        String userIdentifier = extractUserIdentifier(httpRequest);
        LegalCase legalCase = caseService.getCase(identifier, userIdentifier);
        verifyCaseNotDeleted(legalCase);

        LegalCase updatedCase = caseService.updatePriority(identifier, priority, userIdentifier);
        List<MemberResponse> members = caseService.getCaseMembers(identifier, userIdentifier);
        return ResponseEntity.ok(CaseResponse.fromEntity(updatedCase, members));
    }

    @PatchMapping("/{identifier}/due-date")
    public ResponseEntity<CaseResponse> updateDueDate(
            @PathVariable String identifier,
            @RequestParam String dueDate,
            HttpServletRequest httpRequest) {

        String userIdentifier = extractUserIdentifier(httpRequest);
        LegalCase legalCase = caseService.getCase(identifier, userIdentifier);
        verifyCaseNotDeleted(legalCase);

        LegalCase updatedCase = caseService.updateDueDate(identifier, LocalDate.parse(dueDate), userIdentifier);
        List<MemberResponse> members = caseService.getCaseMembers(identifier, userIdentifier);
        return ResponseEntity.ok(CaseResponse.fromEntity(updatedCase, members));
    }

    @PatchMapping("/{identifier}/lock")
    public ResponseEntity<CaseResponse> setLocked(
            @PathVariable String identifier,
            @RequestParam boolean locked,
            HttpServletRequest httpRequest) {

        String userIdentifier = extractUserIdentifier(httpRequest);
        LegalCase legalCase = caseService.getCase(identifier, userIdentifier);
        verifyCaseNotDeleted(legalCase);

        LegalCase updatedCase = caseService.setLocked(identifier, locked, userIdentifier);
        List<MemberResponse> members = caseService.getCaseMembers(identifier, userIdentifier);
        return ResponseEntity.ok(CaseResponse.fromEntity(updatedCase, members));
    }

    // ============================================
    // MEMBER MANAGEMENT (with deleted case check)
    // ============================================

    @PostMapping("/{identifier}/members")
    public ResponseEntity<MemberResponse> addMember(
            @PathVariable String identifier,
            @Valid @RequestBody AddMemberRequest request,
            HttpServletRequest httpRequest) {

        String requesterIdentifier = extractUserIdentifier(httpRequest);
        LegalCase legalCase = caseService.getCase(identifier, requesterIdentifier);
        verifyCaseNotDeleted(legalCase);

        CaseMember member = caseService.addMemberToCaseByIdentifier(
                identifier, request.getIdentifier(), CaseMemberRole.STAFF, requesterIdentifier);
        return ResponseEntity.status(HttpStatus.CREATED).body(MemberResponse.fromEntity(member));
    }

    @PostMapping("/{identifier}/members/bulk")
    public ResponseEntity<List<MemberResponse>> addMembersBulk(
            @PathVariable String identifier,
            @Valid @RequestBody AddMembersRequest request,
            HttpServletRequest httpRequest) {

        String requesterIdentifier = extractUserIdentifier(httpRequest);
        LegalCase legalCase = caseService.getCase(identifier, requesterIdentifier);
        verifyCaseNotDeleted(legalCase);

        List<CaseMember> members = caseService.addMembersToCaseByIdentifiers(
                identifier, request.getIdentifiers(), requesterIdentifier);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(members.stream().map(MemberResponse::fromEntity).collect(Collectors.toList()));
    }

    @PatchMapping("/{identifier}/members/{userIdentifier}/promote")
    public ResponseEntity<MemberResponse> promoteMemberToLawyer(
            @Parameter(description = "Case ID or Case Number") @PathVariable String identifier,
            @Parameter(description = "User identifier to promote") @PathVariable String userIdentifier,
            HttpServletRequest httpRequest) {

        String requesterIdentifier = extractUserIdentifier(httpRequest);
        LegalCase legalCase = caseService.getCase(identifier, requesterIdentifier);
        verifyCaseNotDeleted(legalCase);

        CaseMember caseMember = caseService.promoteMemberToLawyer(identifier, userIdentifier, requesterIdentifier);
        return ResponseEntity.ok(MemberResponse.fromEntity(caseMember));
    }

    @PatchMapping("/{identifier}/members/{userIdentifier}/demote")
    public ResponseEntity<MemberResponse> demoteLawyerToStaff(
            @Parameter(description = "Case ID or Case Number") @PathVariable String identifier,
            @Parameter(description = "User identifier to demote") @PathVariable String userIdentifier,
            HttpServletRequest httpRequest) {

        String requesterIdentifier = extractUserIdentifier(httpRequest);
        LegalCase legalCase = caseService.getCase(identifier, requesterIdentifier);
        verifyCaseNotDeleted(legalCase);

        CaseMember caseMember = caseService.demoteLawyerToStaff(identifier, userIdentifier, requesterIdentifier);
        return ResponseEntity.ok(MemberResponse.fromEntity(caseMember));
    }

    @GetMapping("/{identifier}/members")
    public ResponseEntity<List<MemberResponse>> getMembers(
            @PathVariable String identifier,
            HttpServletRequest httpRequest) {

        String userIdentifier = extractUserIdentifier(httpRequest);
        LegalCase legalCase = caseService.getCase(identifier, userIdentifier);
        verifyCaseNotDeleted(legalCase);

        return ResponseEntity.ok(caseService.getCaseMembers(identifier, userIdentifier));
    }

    @DeleteMapping("/{identifier}/members/{userIdentifierToRemove}")
    public ResponseEntity<Void> removeMember(
            @PathVariable String identifier,
            @PathVariable String userIdentifierToRemove,
            HttpServletRequest httpRequest) {

        String requesterIdentifier = extractUserIdentifier(httpRequest);
        LegalCase legalCase = caseService.getCase(identifier, requesterIdentifier);
        verifyCaseNotDeleted(legalCase);

        caseService.removeMemberFromCase(identifier, userIdentifierToRemove, requesterIdentifier);
        return ResponseEntity.noContent().build();
    }

    // ============================================
    // PROGRESS
    // ============================================

    @GetMapping("/{identifier}/progress")
    public ResponseEntity<Map<String, Object>> getProgress(
            @PathVariable String identifier,
            HttpServletRequest httpRequest) {

        String userIdentifier = extractUserIdentifier(httpRequest);
        LegalCase legalCase = caseService.getCase(identifier, userIdentifier);
        verifyCaseNotDeleted(legalCase);

        Map<String, Object> progress = new HashMap<>();
        progress.put("progressPercentage", caseService.getCaseProgressPercentage(identifier, userIdentifier));
        progress.put("readyForInProgress", caseService.isReadyForInProgress(identifier, userIdentifier));
        progress.put("readyForClosed", caseService.isReadyForClosed(identifier, userIdentifier));
        return ResponseEntity.ok(progress);
    }

    // ============================================
    // SEARCH & FILTERS
    // ============================================

    @GetMapping("/search")
    public ResponseEntity<List<CaseResponse>> searchCases(
            @RequestParam String q,
            HttpServletRequest httpRequest) {

        String userIdentifier = extractUserIdentifier(httpRequest);
        List<LegalCase> cases = caseService.searchUserCases(q, userIdentifier);
        List<CaseResponse> responses = cases.stream()
                .filter(c -> !c.isDeleted())
                .map(c -> buildCaseResponse(c, caseService.getCaseMembers(c.getCaseNumber(), userIdentifier), userIdentifier))
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/search/status/{status}")
    public ResponseEntity<List<CaseResponse>> getCasesByStatus(
            @PathVariable CaseStatus status,
            HttpServletRequest httpRequest) {

        String userIdentifier = extractUserIdentifier(httpRequest);
        List<LegalCase> cases = caseService.getCasesByStatus(status, userIdentifier);
        List<CaseResponse> responses = cases.stream()
                .filter(c -> !c.isDeleted())
                .map(c -> buildCaseResponse(c, caseService.getCaseMembers(c.getCaseNumber(), userIdentifier), userIdentifier))
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/search/priority/{priority}")
    public ResponseEntity<List<CaseResponse>> getCasesByPriority(
            @PathVariable CasePriority priority,
            HttpServletRequest httpRequest) {

        String userIdentifier = extractUserIdentifier(httpRequest);
        List<LegalCase> cases = caseService.getCasesByPriority(priority, userIdentifier);
        List<CaseResponse> responses = cases.stream()
                .filter(c -> !c.isDeleted())
                .map(c -> buildCaseResponse(c, caseService.getCaseMembers(c.getCaseNumber(), userIdentifier), userIdentifier))
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/search/type/{type}")
    public ResponseEntity<List<CaseResponse>> getCasesByType(
            @PathVariable CaseType type,
            HttpServletRequest httpRequest) {

        String userIdentifier = extractUserIdentifier(httpRequest);
        List<LegalCase> cases = caseService.getCasesByType(type, userIdentifier);
        List<CaseResponse> responses = cases.stream()
                .filter(c -> !c.isDeleted())
                .map(c -> buildCaseResponse(c, caseService.getCaseMembers(c.getCaseNumber(), userIdentifier), userIdentifier))
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/search/assigned-to/{targetUserIdentifier}")
    public ResponseEntity<List<CaseResponse>> getCasesByAssignedUser(
            @PathVariable String targetUserIdentifier,
            HttpServletRequest httpRequest) {

        String requesterIdentifier = extractUserIdentifier(httpRequest);
        List<LegalCase> cases = caseService.getCasesByAssignedUser(targetUserIdentifier, requesterIdentifier);
        List<CaseResponse> responses = cases.stream()
                .filter(c -> !c.isDeleted())
                .map(c -> buildCaseResponse(c, caseService.getCaseMembers(c.getCaseNumber(), requesterIdentifier), requesterIdentifier))
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/search/created-by/{creatorIdentifier}")
    public ResponseEntity<List<CaseResponse>> getCasesByCreator(
            @PathVariable String creatorIdentifier,
            HttpServletRequest httpRequest) {

        String requesterIdentifier = extractUserIdentifier(httpRequest);
        List<LegalCase> cases = caseService.getCasesByCreator(creatorIdentifier, requesterIdentifier);
        List<CaseResponse> responses = cases.stream()
                .filter(c -> !c.isDeleted())
                .map(c -> buildCaseResponse(c, caseService.getCaseMembers(c.getCaseNumber(), requesterIdentifier), requesterIdentifier))
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/search/due-date/before/{date}")
    public ResponseEntity<List<CaseResponse>> getCasesDueBefore(
            @PathVariable LocalDate date,
            HttpServletRequest httpRequest) {

        String userIdentifier = extractUserIdentifier(httpRequest);
        List<LegalCase> cases = caseService.getCasesDueBefore(userIdentifier, date);
        List<CaseResponse> responses = cases.stream()
                .filter(c -> !c.isDeleted())
                .map(c -> buildCaseResponse(c, caseService.getCaseMembers(c.getCaseNumber(), userIdentifier), userIdentifier))
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/search/due-date/after/{date}")
    public ResponseEntity<List<CaseResponse>> getCasesDueAfter(
            @PathVariable LocalDate date,
            HttpServletRequest httpRequest) {

        String userIdentifier = extractUserIdentifier(httpRequest);
        List<LegalCase> cases = caseService.getCasesDueAfter(userIdentifier, date);
        List<CaseResponse> responses = cases.stream()
                .filter(c -> !c.isDeleted())
                .map(c -> buildCaseResponse(c, caseService.getCaseMembers(c.getCaseNumber(), userIdentifier), userIdentifier))
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/search/due-date/between")
    public ResponseEntity<List<CaseResponse>> getCasesDueBetween(
            @RequestParam LocalDate start,
            @RequestParam LocalDate end,
            HttpServletRequest httpRequest) {

        String userIdentifier = extractUserIdentifier(httpRequest);
        List<LegalCase> cases = caseService.getCasesDueBetween(userIdentifier, start, end);
        List<CaseResponse> responses = cases.stream()
                .filter(c -> !c.isDeleted())
                .map(c -> buildCaseResponse(c, caseService.getCaseMembers(c.getCaseNumber(), userIdentifier), userIdentifier))
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    // ============================================
    // DELETE ENDPOINTS
    // ============================================

    @DeleteMapping("/{identifier}")
    public ResponseEntity<Void> softDeleteCase(
            @PathVariable String identifier,
            HttpServletRequest httpRequest) {

        String userIdentifier = extractUserIdentifier(httpRequest);
        caseService.softDeleteCase(identifier, userIdentifier);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{identifier}/restore")
    public ResponseEntity<Void> restoreCase(
            @PathVariable String identifier,
            HttpServletRequest httpRequest) {

        String userIdentifier = extractUserIdentifier(httpRequest);
        caseService.restoreCase(identifier, userIdentifier);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/deleted")
    public ResponseEntity<List<CaseResponse>> getDeletedCases(HttpServletRequest httpRequest) {
        String userIdentifier = extractUserIdentifier(httpRequest);
        List<LegalCase> deletedCases = caseService.getDeletedCases(userIdentifier);
        List<CaseResponse> responses = deletedCases.stream()
                .map(c -> CaseResponse.fromEntity(c, List.of()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    // ============================================
    // HELPERS
    // ============================================

    private CaseResponse buildCaseResponse(LegalCase legalCase, List<MemberResponse> members, String userIdentifier) {
        CaseResponse response = CaseResponse.fromEntity(legalCase, members);
        response.setReadyForInProgress(caseService.isReadyForInProgress(legalCase.getCaseNumber(), userIdentifier));
        response.setReadyForClosed(caseService.isReadyForClosed(legalCase.getCaseNumber(), userIdentifier));
        response.setProgressPercentage(caseService.getCaseProgressPercentage(legalCase.getCaseNumber(), userIdentifier));
        return response;
    }

    private String extractUserIdentifier(HttpServletRequest request) {
        String token = extractToken(request);
        String email = jwtUtils.getEmailFromToken(token);
        return email;
    }

    private String extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new RuntimeException("Missing or invalid Authorization header");
        }
        return authHeader.substring(7);
    }

    private void verifyCaseNotDeleted(LegalCase legalCase) {
        if (legalCase.isDeleted()) {
            throw new InvalidStatusTransitionException("Cannot perform actions on a deleted case");
        }
    }
}