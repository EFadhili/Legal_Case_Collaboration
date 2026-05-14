package com.legalcase.service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legalcase.dto.request.*;
import com.legalcase.dto.response.*;
import com.legalcase.entity.*;
import com.legalcase.enums.*;
import com.legalcase.repository.*;
import com.legalcase.security.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.net.URL;
import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Full System Integration Tests (All Modules)")
class FullSystemIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CaseRepository caseRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private CaseMemberRepository caseMemberRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private JwtUtils jwtUtils;

    @MockBean
    private AmazonS3 amazonS3;

    // Test data
    private String lawyerToken;
    private String staffToken;
    private Long lawyerId;
    private Long staffId;
    private Long caseId;
    private Long taskId;
    private Long commentId;
    private Long documentId;

    @BeforeEach
    void setUp() throws Exception {
        // Clear all repositories
        chatMessageRepository.deleteAll();
        notificationRepository.deleteAll();
        documentRepository.deleteAll();
        commentRepository.deleteAll();
        taskRepository.deleteAll();
        caseMemberRepository.deleteAll();
        caseRepository.deleteAll();
        userRepository.deleteAll();

        // ============================================
        // MOCK AWS S3 OPERATIONS CORRECTLY
        // ============================================

        // For void methods - use doNothing()
        when(amazonS3.putObject(any(PutObjectRequest.class)))
                .thenReturn(null);
        doNothing().when(amazonS3).deleteObject(anyString(), anyString());

        // For methods that return values - use when().thenReturn()
        S3Object mockS3Object = new S3Object();
        mockS3Object.setObjectContent(new ByteArrayInputStream("Mock file content".getBytes()));
        when(amazonS3.getObject(anyString(), anyString())).thenReturn(mockS3Object);

        // generatePresignedUrl returns URL - use when().thenReturn()
        when(amazonS3.generatePresignedUrl(any(), any(), any())).thenReturn(new URL("https://mock-s3-url.com/test.pdf"));

        // getUrl returns URL - use when().thenReturn()
        when(amazonS3.getUrl(anyString(), anyString())).thenReturn(new URL("https://mock-s3-url.com/test.pdf"));

        // ============================================
        // REGISTER USERS
        // ============================================

        // Register Lawyer
        RegisterRequest lawyerRequest = new RegisterRequest();
        lawyerRequest.setUsername("lawyerjohn");
        lawyerRequest.setEmail("lawyer@legalfirm.com");
        lawyerRequest.setPassword("SecurePass123!");
        lawyerRequest.setFullName("John Lawyer");
        lawyerRequest.setRole(Role.LAWYER);

        MvcResult lawyerResult = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(lawyerRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        String lawyerResponseJson = lawyerResult.getResponse().getContentAsString();
        AuthResponse lawyerAuthResponse = objectMapper.readValue(lawyerResponseJson, AuthResponse.class);
        lawyerToken = lawyerAuthResponse.getToken();
        lawyerId = lawyerAuthResponse.getUser().getId();

        // Register Staff
        RegisterRequest staffRequest = new RegisterRequest();
        staffRequest.setUsername("staffjane");
        staffRequest.setEmail("staff@legalfirm.com");
        staffRequest.setPassword("SecurePass123!");
        staffRequest.setFullName("Jane Staff");
        staffRequest.setRole(Role.STAFF);

        MvcResult staffResult = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(staffRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        String staffResponseJson = staffResult.getResponse().getContentAsString();
        AuthResponse staffAuthResponse = objectMapper.readValue(staffResponseJson, AuthResponse.class);
        staffToken = staffAuthResponse.getToken();
        staffId = staffAuthResponse.getUser().getId();

        // Register Admin
        RegisterRequest adminRequest = new RegisterRequest();
        adminRequest.setUsername("admin");
        adminRequest.setEmail("admin@legalfirm.com");
        adminRequest.setPassword("SecurePass123!");
        adminRequest.setFullName("System Admin");
        adminRequest.setRole(Role.ADMIN);

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(adminRequest)))
                .andExpect(status().isCreated());

        // ============================================
        // CREATE CASE
        // ============================================

        CreateCaseRequest caseRequest = new CreateCaseRequest();
        caseRequest.setTitle("Smith vs Johnson Contract Dispute");
        caseRequest.setDescription("Legal dispute over breach of contract in real estate sale.");
        caseRequest.setType(CaseType.CIVIL);
        caseRequest.setPriority(CasePriority.HIGH);
        caseRequest.setDueDate(LocalDate.now().plusDays(60));

        MvcResult caseResult = mockMvc.perform(post("/cases")
                        .header("Authorization", "Bearer " + lawyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(caseRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        String caseResponseJson = caseResult.getResponse().getContentAsString();
        CaseResponse caseResponse = objectMapper.readValue(caseResponseJson, CaseResponse.class);
        caseId = caseResponse.getId();

        // ============================================
        // ADD STAFF AS CASE MEMBER
        // ============================================

        AddMemberRequest addMemberRequest = new AddMemberRequest();
        addMemberRequest.setIdentifier("staffjane");
        addMemberRequest.setRole(CaseMemberRole.STAFF);

        mockMvc.perform(post("/cases/{caseId}/members", caseId)
                        .header("Authorization", "Bearer " + lawyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addMemberRequest)))
                .andExpect(status().isCreated());

        // ============================================
        // CREATE TASK
        // ============================================

        CreateTaskRequest taskRequest = new CreateTaskRequest();
        taskRequest.setTitle("Review Contract Documents");
        taskRequest.setDescription("Review and analyze the contract for compliance");
        taskRequest.setType(TaskType.MANDATORY);
        taskRequest.setPriority(TaskPriority.HIGH);
        taskRequest.setDueDate(LocalDate.now().plusDays(14));
        taskRequest.setAssignedToUserId(staffId);

        MvcResult taskResult = mockMvc.perform(post("/tasks/case/{caseId}", caseId)
                        .header("Authorization", "Bearer " + lawyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(taskRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        String taskResponseJson = taskResult.getResponse().getContentAsString();
        TaskResponse taskResponse = objectMapper.readValue(taskResponseJson, TaskResponse.class);
        taskId = taskResponse.getId();

        // ============================================
        // CREATE CASE COMMENT
        // ============================================

        CreateCommentRequest caseCommentRequest = new CreateCommentRequest();
        caseCommentRequest.setContent("This case requires immediate attention.");
        caseCommentRequest.setType(CommentType.CASE);
        caseCommentRequest.setCaseId(caseId);

        MvcResult commentResult = mockMvc.perform(post("/comments")
                        .header("Authorization", "Bearer " + lawyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(caseCommentRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        String commentResponseJson = commentResult.getResponse().getContentAsString();
        CommentResponse commentResponse = objectMapper.readValue(commentResponseJson, CommentResponse.class);
        commentId = commentResponse.getId();

        // ============================================
        // UPLOAD DOCUMENT TO CASE
        // ============================================

        MockMultipartFile documentFile = new MockMultipartFile(
                "file",
                "contract.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "Sample PDF content".getBytes()
        );

        MvcResult documentResult = mockMvc.perform(multipart("/documents/case/{caseId}", caseId)
                        .file(documentFile)
                        .param("description", "Signed contract document")
                        .param("tags", "contract,signed")
                        .header("Authorization", "Bearer " + lawyerToken))
                .andExpect(status().isCreated())
                .andReturn();

        String documentResponseJson = documentResult.getResponse().getContentAsString();
        DocumentResponse documentResponse = objectMapper.readValue(documentResponseJson, DocumentResponse.class);
        documentId = documentResponse.getId();
    }

    // ============================================
    // USER MODULE TESTS
    // ============================================

    @Test
    @DisplayName("Full: User can login and receive JWT token")
    void testUserLogin() throws Exception {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("lawyer@legalfirm.com");
        loginRequest.setPassword("SecurePass123!");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.user.username").value("lawyerjohn"));
    }

    // ============================================
    // CASE MODULE TESTS
    // ============================================

    @Test
    @DisplayName("Full: Get case with all details including members and progress")
    void testGetCaseWithDetails() throws Exception {
        mockMvc.perform(get("/cases/{caseId}", caseId)
                        .header("Authorization", "Bearer " + lawyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(caseId))
                .andExpect(jsonPath("$.title").value("Smith vs Johnson Contract Dispute"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.ownerName").value("John Lawyer"));
    }

    // ============================================
    // TASK MODULE TESTS
    // ============================================

    @Test
    @DisplayName("Full: Complete task workflow with approval")
    void testCompleteTaskWorkflow() throws Exception {
        // Step 1: Staff completes task (100% progress auto-moves to REVIEW)
        UpdateTaskProgressRequest completeRequest = new UpdateTaskProgressRequest();
        completeRequest.setProgress(100);

        mockMvc.perform(patch("/tasks/{taskId}/progress", taskId)
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(completeRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progress").value(100))
                .andExpect(jsonPath("$.status").value("REVIEW"));

        // Step 2: Lawyer approves task
        UpdateTaskStatusRequest statusRequest = new UpdateTaskStatusRequest();
        statusRequest.setStatus(TaskStatus.COMPLETED);

        mockMvc.perform(patch("/tasks/{taskId}/status", taskId)
                        .header("Authorization", "Bearer " + lawyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    // ============================================
    // COMMENT MODULE TESTS
    // ============================================

    @Test
    @DisplayName("Full: Create and retrieve task comment")
    void testTaskComment() throws Exception {
        CreateCommentRequest taskCommentRequest = new CreateCommentRequest();
        taskCommentRequest.setContent("The contract has several ambiguous clauses");
        taskCommentRequest.setType(CommentType.TASK);
        taskCommentRequest.setTaskId(taskId);

        mockMvc.perform(post("/comments")
                        .header("Authorization", "Bearer " + lawyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(taskCommentRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").value("The contract has several ambiguous clauses"))
                .andExpect(jsonPath("$.type").value("TASK"))
                .andExpect(jsonPath("$.taskId").value(taskId));
    }

    // ============================================
    // DOCUMENT MODULE TESTS
    // ============================================

    @Test
    @DisplayName("Full: Upload document to case")
    void testUploadDocument() throws Exception {
        MockMultipartFile newFile = new MockMultipartFile(
                "file",
                "evidence.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "Evidence content".getBytes()
        );

        mockMvc.perform(multipart("/documents/case/{caseId}", caseId)
                        .file(newFile)
                        .param("description", "Court evidence")
                        .param("tags", "evidence,court")
                        .header("Authorization", "Bearer " + lawyerToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.originalFileName").value("evidence.pdf"))
                .andExpect(jsonPath("$.fileExtension").value("pdf"))
                .andExpect(jsonPath("$.caseId").value(caseId))
                .andExpect(jsonPath("$.uploadedBy").value("John Lawyer"));
    }

    @Test
    @DisplayName("Full: Get case documents")
    void testGetCaseDocuments() throws Exception {
        mockMvc.perform(get("/documents/case/{caseId}", caseId)
                        .header("Authorization", "Bearer " + lawyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].originalFileName").value("contract.pdf"))
                .andExpect(jsonPath("$[0].fileExtension").value("pdf"));
    }

    @Test
    @DisplayName("Full: Get document by ID")
    void testGetDocumentById() throws Exception {
        mockMvc.perform(get("/documents/{documentId}", documentId)
                        .header("Authorization", "Bearer " + lawyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(documentId))
                .andExpect(jsonPath("$.originalFileName").value("contract.pdf"))
                .andExpect(jsonPath("$.description").value("Signed contract document"))
                .andExpect(jsonPath("$.tags").value("contract,signed"));
    }

    @Test
    @DisplayName("Full: Update document metadata")
    void testUpdateDocumentMetadata() throws Exception {
        String updateJson = "{\"description\": \"Updated contract description\", \"tags\": \"contract,updated,important\"}";

        mockMvc.perform(put("/documents/{documentId}", documentId)
                        .header("Authorization", "Bearer " + lawyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Updated contract description"))
                .andExpect(jsonPath("$.tags").value("contract,updated,important"));
    }

    @Test
    @DisplayName("Full: Download document (redirects to presigned URL)")
    void testDownloadDocument() throws Exception {
        mockMvc.perform(get("/documents/{documentId}/download", documentId)
                        .header("Authorization", "Bearer " + lawyerToken))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().exists("Location"));
    }

    @Test
    @DisplayName("Full: Get extracted text from document")
    void testGetExtractedText() throws Exception {
        mockMvc.perform(get("/documents/{documentId}/text", documentId)
                        .header("Authorization", "Bearer " + lawyerToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Full: Soft delete and restore document")
    void testSoftDeleteAndRestoreDocument() throws Exception {
        // Soft delete
        mockMvc.perform(delete("/documents/{documentId}", documentId)
                        .header("Authorization", "Bearer " + lawyerToken))
                .andExpect(status().isNoContent());

        // Verify it's deleted (should return 404)
        mockMvc.perform(get("/documents/{documentId}", documentId)
                        .header("Authorization", "Bearer " + lawyerToken))
                .andExpect(status().isNotFound());

        // Restore
        mockMvc.perform(post("/documents/{documentId}/restore", documentId)
                        .header("Authorization", "Bearer " + lawyerToken))
                .andExpect(status().isOk());

        // Verify it's restored
        mockMvc.perform(get("/documents/{documentId}", documentId)
                        .header("Authorization", "Bearer " + lawyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalFileName").value("contract.pdf"));
    }

    // ============================================
    // NOTIFICATION MODULE TESTS
    // ============================================

    @Test
    @DisplayName("Full: Get notifications for user")
    void testGetNotifications() throws Exception {
        mockMvc.perform(get("/notifications")
                        .param("page", "0")
                        .param("size", "20")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Full: Get unread notification count")
    void testGetUnreadCount() throws Exception {
        mockMvc.perform(get("/notifications/unread/count")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").exists());
    }

    // ============================================
    // CROSS-MODULE WORKFLOW TESTS
    // ============================================

    @Test
    @DisplayName("Full: Complete case lifecycle with documents")
    void testCompleteCaseLifecycleWithDocuments() throws Exception {
        // Step 1: Create another mandatory task
        CreateTaskRequest secondTask = new CreateTaskRequest();
        secondTask.setTitle("Prepare Legal Memorandum");
        secondTask.setDescription("Prepare legal memorandum");
        secondTask.setType(TaskType.MANDATORY);
        secondTask.setPriority(TaskPriority.MEDIUM);
        secondTask.setDueDate(LocalDate.now().plusDays(21));
        secondTask.setAssignedToUserId(staffId);

        MvcResult secondTaskResult = mockMvc.perform(post("/tasks/case/{caseId}", caseId)
                        .header("Authorization", "Bearer " + lawyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(secondTask)))
                .andExpect(status().isCreated())
                .andReturn();

        String secondTaskJson = secondTaskResult.getResponse().getContentAsString();
        TaskResponse secondTaskResponse = objectMapper.readValue(secondTaskJson, TaskResponse.class);
        Long secondTaskId = secondTaskResponse.getId();

        // Step 2: Move case to IN_PROGRESS
        UpdateCaseStatusRequest statusRequest = new UpdateCaseStatusRequest();
        statusRequest.setStatus(CaseStatus.IN_PROGRESS);

        mockMvc.perform(patch("/cases/{caseId}/status", caseId)
                        .header("Authorization", "Bearer " + lawyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        // Step 3: Upload another document during progress
        MockMultipartFile progressFile = new MockMultipartFile(
                "file",
                "progress-report.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "Progress report content".getBytes()
        );

        mockMvc.perform(multipart("/documents/case/{caseId}", caseId)
                        .file(progressFile)
                        .param("description", "Case progress report")
                        .header("Authorization", "Bearer " + lawyerToken))
                .andExpect(status().isCreated());

        // Step 4: Complete both tasks
        UpdateTaskProgressRequest completeRequest = new UpdateTaskProgressRequest();
        completeRequest.setProgress(100);

        mockMvc.perform(patch("/tasks/{taskId}/progress", taskId)
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(completeRequest)))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/tasks/{taskId}/progress", secondTaskId)
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(completeRequest)))
                .andExpect(status().isOk());

        // Step 5: Approve both tasks
        UpdateTaskStatusRequest approveRequest = new UpdateTaskStatusRequest();
        approveRequest.setStatus(TaskStatus.COMPLETED);

        mockMvc.perform(patch("/tasks/{taskId}/status", taskId)
                        .header("Authorization", "Bearer " + lawyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(approveRequest)))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/tasks/{taskId}/status", secondTaskId)
                        .header("Authorization", "Bearer " + lawyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(approveRequest)))
                .andExpect(status().isOk());

        // Step 6: Close the case
        statusRequest.setStatus(CaseStatus.CLOSED);
        mockMvc.perform(patch("/cases/{caseId}/status", caseId)
                        .header("Authorization", "Bearer " + lawyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.locked").value(true));

        // Step 7: Verify documents are still accessible in closed case
        mockMvc.perform(get("/documents/case/{caseId}", caseId)
                        .header("Authorization", "Bearer " + lawyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("Full: Staff cannot upload document to case after it's locked")
    void testStaffCannotUploadToLockedCase() throws Exception {
        // First, close the case (requires tasks to be completed)
        UpdateTaskProgressRequest completeRequest = new UpdateTaskProgressRequest();
        completeRequest.setProgress(100);
        mockMvc.perform(patch("/tasks/{taskId}/progress", taskId)
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(completeRequest)))
                .andExpect(status().isOk());

        UpdateTaskStatusRequest approveRequest = new UpdateTaskStatusRequest();
        approveRequest.setStatus(TaskStatus.COMPLETED);
        mockMvc.perform(patch("/tasks/{taskId}/status", taskId)
                        .header("Authorization", "Bearer " + lawyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(approveRequest)))
                .andExpect(status().isOk());

        UpdateCaseStatusRequest closeRequest = new UpdateCaseStatusRequest();
        closeRequest.setStatus(CaseStatus.CLOSED);
        mockMvc.perform(patch("/cases/{caseId}/status", caseId)
                        .header("Authorization", "Bearer " + lawyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(closeRequest)))
                .andExpect(status().isOk());

        // Try to upload document to locked case (should fail)
        MockMultipartFile newFile = new MockMultipartFile(
                "file",
                "new-doc.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "New content".getBytes()
        );

        mockMvc.perform(multipart("/documents/case/{caseId}", caseId)
                        .file(newFile)
                        .param("description", "New document")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isBadRequest());
    }

    // ============================================
    // SEARCH TESTS
    // ============================================

    @Test
    @DisplayName("Full: Search for users (autocomplete)")
    void testSearchUsers() throws Exception {
        mockMvc.perform(get("/users/autocomplete")
                        .param("partial", "lawyer")
                        .header("Authorization", "Bearer " + lawyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("lawyerjohn"))
                .andExpect(jsonPath("$[0].email").value("lawyer@legalfirm.com"));
    }

    @Test
    @DisplayName("Full: Get case progress with documents count")
    void testGetCaseProgress() throws Exception {
        mockMvc.perform(get("/cases/{caseId}/progress", caseId)
                        .header("Authorization", "Bearer " + lawyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progressPercentage").exists())
                .andExpect(jsonPath("$.readyForInProgress").exists())
                .andExpect(jsonPath("$.readyForClosed").exists());
    }
}


