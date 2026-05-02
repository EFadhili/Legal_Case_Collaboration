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
import com.legalcase.security.JwtUtils;
import com.legalcase.service.CaseService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
public class CaseController {

    private final CaseService caseService;
    private final JwtUtils jwtUtils;

    @PostMapping
    public ResponseEntity<CaseResponse> createCase(
            @Valid @RequestBody CreateCaseRequest request,
            HttpServletRequest httpRequest) {

        Long userId = extractUserId(httpRequest);

        LegalCase legalCase = caseService.createCase(
                request.getTitle(),
                request.getDescription(),
                request.getType(),
                request.getPriority(),
                request.getDueDate(),
                userId,
                request.getAssignedUserIds()
        );

        List<MemberResponse> members = caseService.getCaseMembers(legalCase.getId());
        CaseResponse response = CaseResponse.fromEntity(legalCase, members);

        response.setReadyForInProgress(caseService.isReadyForInProgress(legalCase.getId()));
        response.setReadyForClosed(caseService.isReadyForClosed(legalCase.getId()));
        response.setProgressPercentage(caseService.getCaseProgressPercentage(legalCase.getId()));

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<CaseResponse>> getAllCases() {
        // TODO: Add pagination
        return ResponseEntity.ok(List.of());
    }

    @GetMapping("/{id}")
    public ResponseEntity<CaseResponse> getCaseById(@PathVariable Long id) {
        try {
            LegalCase legalCase = caseService.findById(id);
            List<MemberResponse> members = caseService.getCaseMembers(id);
            CaseResponse response = CaseResponse.fromEntity(legalCase, members);

            response.setReadyForInProgress(caseService.isReadyForInProgress(id));
            response.setReadyForClosed(caseService.isReadyForClosed(id));
            response.setProgressPercentage(caseService.getCaseProgressPercentage(id));

            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            log.error("Case not found: {}", id, e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @GetMapping("/my-cases")
    public ResponseEntity<Map<String, List<CaseResponse>>> getMyCases(HttpServletRequest httpRequest) {
        Long userId = extractUserId(httpRequest);

        List<CaseResponse> ownedCases = caseService.getCasesByOwner(userId)
                .stream()
                .map(c -> {
                    List<MemberResponse> members = caseService.getCaseMembers(c.getId());
                    CaseResponse response = CaseResponse.fromEntity(c, members);
                    response.setReadyForInProgress(caseService.isReadyForInProgress(c.getId()));
                    response.setReadyForClosed(caseService.isReadyForClosed(c.getId()));
                    response.setProgressPercentage(caseService.getCaseProgressPercentage(c.getId()));
                    return response;
                })
                .collect(Collectors.toList());

        List<CaseResponse> memberCases = caseService.getCasesByMember(userId)
                .stream()
                .filter(c -> !c.getOwner().getId().equals(userId))
                .map(c -> {
                    List<MemberResponse> members = caseService.getCaseMembers(c.getId());
                    CaseResponse response = CaseResponse.fromEntity(c, members);
                    response.setReadyForInProgress(caseService.isReadyForInProgress(c.getId()));
                    response.setReadyForClosed(caseService.isReadyForClosed(c.getId()));
                    response.setProgressPercentage(caseService.getCaseProgressPercentage(c.getId()));
                    return response;
                })
                .collect(Collectors.toList());

        Map<String, List<CaseResponse>> response = new HashMap<>();
        response.put("createdByMe", ownedCases);
        response.put("assignedToMe", memberCases);

        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<CaseResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateCaseStatusRequest request,
            HttpServletRequest httpRequest) {

        Long userId = extractUserId(httpRequest);

        LegalCase legalCase = caseService.updateStatus(id, request.getStatus(), userId);
        List<MemberResponse> members = caseService.getCaseMembers(id);
        CaseResponse response = CaseResponse.fromEntity(legalCase, members);

        response.setReadyForInProgress(caseService.isReadyForInProgress(id));
        response.setReadyForClosed(caseService.isReadyForClosed(id));
        response.setProgressPercentage(caseService.getCaseProgressPercentage(id));

        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/priority")
    public ResponseEntity<CaseResponse> updatePriority(
            @PathVariable Long id,
            @RequestParam CasePriority priority,
            HttpServletRequest httpRequest) {

        Long userId = extractUserId(httpRequest);

        LegalCase legalCase = caseService.updatePriority(id, priority, userId);
        List<MemberResponse> members = caseService.getCaseMembers(id);
        return ResponseEntity.ok(CaseResponse.fromEntity(legalCase, members));
    }

    @PatchMapping("/{id}/due-date")
    public ResponseEntity<CaseResponse> updateDueDate(
            @PathVariable Long id,
            @RequestParam String dueDate,
            HttpServletRequest httpRequest) {

        Long userId = extractUserId(httpRequest);

        LegalCase legalCase = caseService.updateDueDate(id, LocalDate.parse(dueDate), userId);
        List<MemberResponse> members = caseService.getCaseMembers(id);
        return ResponseEntity.ok(CaseResponse.fromEntity(legalCase, members));
    }

    @PatchMapping("/{id}/lock")
    public ResponseEntity<CaseResponse> setLocked(
            @PathVariable Long id,
            @RequestParam boolean locked,
            HttpServletRequest httpRequest) {

        Long userId = extractUserId(httpRequest);

        LegalCase legalCase = caseService.setLocked(id, locked, userId);
        List<MemberResponse> members = caseService.getCaseMembers(id);
        return ResponseEntity.ok(CaseResponse.fromEntity(legalCase, members));
    }

    @PostMapping("/{id}/members")
    public ResponseEntity<MemberResponse> addMember(
            @PathVariable Long id,
            @Valid @RequestBody AddMemberRequest request,
            HttpServletRequest httpRequest) {

        Long requesterId = extractUserId(httpRequest);

        // Check permission: only case lawyers or admins can add members
        if (!caseService.isCaseMemberWithRole(id, requesterId, CaseMemberRole.LAWYER) && !isAdmin(httpRequest)) {
            throw new RuntimeException("Only case lawyers or admins can add new members");
        }

        // Use identifier-based method (supports username OR email)
        CaseMember member = caseService.addMemberToCaseByIdentifier(id, request.getIdentifier(), request.getRole(), requesterId);

        return ResponseEntity.status(HttpStatus.CREATED).body(MemberResponse.fromEntity(member));
    }

    @PostMapping("/{id}/members/bulk")
    public ResponseEntity<List<MemberResponse>> addMembersBulk(
            @PathVariable Long id,
            @Valid @RequestBody AddMembersRequest request,
            HttpServletRequest httpRequest) {

        Long requesterId = extractUserId(httpRequest);

        if (!caseService.isCaseMemberWithRole(id, requesterId, CaseMemberRole.LAWYER) && !isAdmin(httpRequest)) {
            throw new RuntimeException("Only case lawyers or admins can add new members");
        }

        // NEW: Use identifier-based method (supports username OR email for each identifier)
        List<com.legalcase.entity.CaseMember> members = caseService.addMembersToCaseByIdentifiers(
                id, request.getIdentifiers(), request.getRole(), requesterId);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(members.stream().map(MemberResponse::fromEntity).collect(Collectors.toList()));
    }

    @GetMapping("/{id}/members")
    public ResponseEntity<List<MemberResponse>> getMembers(@PathVariable Long id) {
        return ResponseEntity.ok(caseService.getCaseMembers(id));
    }

    @DeleteMapping("/{id}/members/{userId}")
    public ResponseEntity<Void> removeMember(
            @PathVariable Long id,
            @PathVariable Long userId,
            HttpServletRequest httpRequest) {

        Long requesterId = extractUserId(httpRequest);
        caseService.removeMemberFromCase(id, userId, requesterId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/progress")
    public ResponseEntity<Map<String, Object>> getProgress(@PathVariable Long id) {
        Map<String, Object> progress = new HashMap<>();
        progress.put("progressPercentage", caseService.getCaseProgressPercentage(id));
        progress.put("readyForInProgress", caseService.isReadyForInProgress(id));
        progress.put("readyForClosed", caseService.isReadyForClosed(id));
        return ResponseEntity.ok(progress);
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

    private boolean isAdmin(HttpServletRequest request) {
        try {
            String token = extractToken(request);
            String role = jwtUtils.getRoleFromToken(token);
            return "ADMIN".equals(role);
        } catch (Exception e) {
            return false;
        }
    }
}




