package com.legalcase.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.legalcase.dto.request.CreateCaseRequest;
import com.legalcase.entity.LegalCase;
import com.legalcase.entity.User;
import com.legalcase.enums.CasePriority;
import com.legalcase.enums.CaseStatus;
import com.legalcase.enums.CaseType;
import com.legalcase.enums.Role;
import com.legalcase.security.JwtUtils;
import com.legalcase.service.CaseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Case Controller Unit Tests")
class CaseControllerUnitTests {

    @Mock
    private CaseService caseService;

    @Mock
    private JwtUtils jwtUtils;

    @InjectMocks
    private CaseController caseController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private CreateCaseRequest createCaseRequest;
    private LegalCase mockLegalCase;
    private User mockOwner;
    private String mockToken = "Bearer mock.jwt.token";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(caseController).build();

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        // Setup Mock Owner (ADDED username)
        mockOwner = new User();
        mockOwner.setId(1L);
        mockOwner.setUsername("lawyer");
        mockOwner.setEmail("lawyer@test.com");
        mockOwner.setFullName("Test Lawyer");
        mockOwner.setRole(Role.LAWYER);

        mockLegalCase = new LegalCase();
        mockLegalCase.setId(1L);
        mockLegalCase.setCaseNumber("CASE-2026-00001");
        mockLegalCase.setTitle("Test Case");
        mockLegalCase.setDescription("Test Description");
        mockLegalCase.setType(CaseType.CIVIL);
        mockLegalCase.setPriority(CasePriority.MEDIUM);
        mockLegalCase.setStatus(CaseStatus.OPEN);
        mockLegalCase.setOwner(mockOwner);
        mockLegalCase.setFilingDate(LocalDate.now());
        mockLegalCase.setCreatedAt(LocalDateTime.now());
        mockLegalCase.setUpdatedAt(LocalDateTime.now());

        createCaseRequest = new CreateCaseRequest();
        createCaseRequest.setTitle("Test Case");
        createCaseRequest.setDescription("Test Description");
        createCaseRequest.setType(CaseType.CIVIL);
        createCaseRequest.setPriority(CasePriority.MEDIUM);
        createCaseRequest.setDueDate(LocalDate.now().plusDays(30));

        Set<Long> assignedUserIds = new HashSet<>();
        assignedUserIds.add(2L);
        createCaseRequest.setAssignedUserIds(assignedUserIds);
    }

    @Test
    @DisplayName("POST /api/cases - Should return 201 when case is created")
    void createCase_Success_Returns201() throws Exception {
        when(jwtUtils.getUserIdFromToken(anyString())).thenReturn(1L);

        when(caseService.createCase(
                anyString(),
                anyString(),
                any(CaseType.class),
                any(CasePriority.class),
                any(LocalDate.class),
                anyLong(),
                anySet()
        )).thenReturn(mockLegalCase);

        when(caseService.getCaseMembers(anyLong())).thenReturn(new ArrayList<>());
        when(caseService.isReadyForInProgress(anyLong())).thenReturn(false);
        when(caseService.isReadyForClosed(anyLong())).thenReturn(false);
        when(caseService.getCaseProgressPercentage(anyLong())).thenReturn(0);

        mockMvc.perform(post("/cases")
                        .header("Authorization", mockToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createCaseRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.caseNumber").value("CASE-2026-00001"))
                .andExpect(jsonPath("$.title").value("Test Case"))
                .andExpect(jsonPath("$.status").value("OPEN"));

        verify(caseService, times(1)).createCase(
                anyString(),
                anyString(),
                any(CaseType.class),
                any(CasePriority.class),
                any(LocalDate.class),
                anyLong(),
                anySet()
        );
    }

    @Test
    @DisplayName("GET /api/cases/{id} - Should return 200 when case exists")
    void getCaseById_Success_Returns200() throws Exception {
        when(caseService.findById(1L)).thenReturn(mockLegalCase);
        when(caseService.getCaseMembers(1L)).thenReturn(new ArrayList<>());
        when(caseService.isReadyForInProgress(1L)).thenReturn(false);
        when(caseService.isReadyForClosed(1L)).thenReturn(false);
        when(caseService.getCaseProgressPercentage(1L)).thenReturn(0);

        mockMvc.perform(get("/cases/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.caseNumber").value("CASE-2026-00001"))
                .andExpect(jsonPath("$.title").value("Test Case"))
                .andExpect(jsonPath("$.ownerName").value("Test Lawyer"));

        verify(caseService, times(1)).findById(1L);
    }

    @Test
    @DisplayName("GET /api/cases/{id} - Should return 404 when case doesn't exist")
    void getCaseById_NotFound_Returns404() throws Exception {
        when(caseService.findById(999L))
                .thenThrow(new RuntimeException("Case not found with ID: 999"));

        mockMvc.perform(get("/cases/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PATCH /api/cases/{id}/status - Should return 200 when status updated")
    void updateStatus_Success_Returns200() throws Exception {
        mockLegalCase.setStatus(CaseStatus.IN_PROGRESS);

        when(jwtUtils.getUserIdFromToken(anyString())).thenReturn(1L);
        when(caseService.updateStatus(eq(1L), eq(CaseStatus.IN_PROGRESS), eq(1L)))
                .thenReturn(mockLegalCase);
        when(caseService.getCaseMembers(1L)).thenReturn(new ArrayList<>());
        when(caseService.isReadyForInProgress(1L)).thenReturn(true);
        when(caseService.isReadyForClosed(1L)).thenReturn(false);
        when(caseService.getCaseProgressPercentage(1L)).thenReturn(50);

        String statusRequest = "{\"status\": \"IN_PROGRESS\"}";

        mockMvc.perform(patch("/cases/1/status")
                        .header("Authorization", mockToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        verify(caseService, times(1)).updateStatus(eq(1L), eq(CaseStatus.IN_PROGRESS), eq(1L));
    }

    @Test
    @DisplayName("PATCH /api/cases/{id}/priority - Should return 200 when priority updated")
    void updatePriority_Success_Returns200() throws Exception {
        mockLegalCase.setPriority(CasePriority.URGENT);

        when(jwtUtils.getUserIdFromToken(anyString())).thenReturn(1L);
        when(caseService.updatePriority(eq(1L), eq(CasePriority.URGENT), eq(1L)))
                .thenReturn(mockLegalCase);
        when(caseService.getCaseMembers(1L)).thenReturn(new ArrayList<>());

        mockMvc.perform(patch("/cases/1/priority?priority=URGENT")
                        .header("Authorization", mockToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priority").value("URGENT"));

        verify(caseService, times(1)).updatePriority(eq(1L), eq(CasePriority.URGENT), eq(1L));
    }

    @Test
    @DisplayName("PATCH /api/cases/{id}/lock - Should return 200 when case is locked")
    void setLocked_Success_Returns200() throws Exception {
        mockLegalCase.setLocked(true);

        when(jwtUtils.getUserIdFromToken(anyString())).thenReturn(1L);
        when(caseService.setLocked(eq(1L), eq(true), eq(1L)))
                .thenReturn(mockLegalCase);
        when(caseService.getCaseMembers(1L)).thenReturn(new ArrayList<>());

        mockMvc.perform(patch("/cases/1/lock?locked=true")
                        .header("Authorization", mockToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locked").value(true));

        verify(caseService, times(1)).setLocked(eq(1L), eq(true), eq(1L));
    }

    @Test
    @DisplayName("GET /api/cases/{id}/progress - Should return progress information")
    void getProgress_Success_Returns200() throws Exception {
        when(caseService.getCaseProgressPercentage(1L)).thenReturn(75);
        when(caseService.isReadyForInProgress(1L)).thenReturn(true);
        when(caseService.isReadyForClosed(1L)).thenReturn(false);

        mockMvc.perform(get("/cases/1/progress"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progressPercentage").value(75))
                .andExpect(jsonPath("$.readyForInProgress").value(true))
                .andExpect(jsonPath("$.readyForClosed").value(false));
    }
}

