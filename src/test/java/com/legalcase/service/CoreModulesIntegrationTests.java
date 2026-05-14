package com.legalcase.service;

import com.amazonaws.services.s3.AmazonS3;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legalcase.dto.request.*;
import com.legalcase.dto.response.*;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Core Modules Integration Tests")
class CoreModulesIntegrationTests {

    @MockBean
    private AmazonS3 amazonS3;

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
    private CaseMemberRepository caseMemberRepository;

    @Autowired
    private JwtUtils jwtUtils;

    // Test data
    private String lawyerToken;
    private String staffToken;
    private Long lawyerId;
    private Long staffId;
    private Long caseId;
    private Long taskId;
    private Long commentId;

    @BeforeEach
    void setUp() throws Exception {
        // Clean up existing data
        commentRepository.deleteAll();
        taskRepository.deleteAll();
        caseMemberRepository.deleteAll();
        caseRepository.deleteAll();
        userRepository.deleteAll();

        // ============================================
        // STEP 1: Register users
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

        // ============================================
        // STEP 2: Create a Case (as Lawyer)
        // ============================================

        CreateCaseRequest caseRequest = new CreateCaseRequest();
        caseRequest.setTitle("Smith vs Johnson Contract Dispute");
        caseRequest.setDescription("Legal dispute over breach of contract in real estate sale. Amount: $500,000");
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

        assertThat(caseId).isNotNull();
        assertThat(caseResponse.getTitle()).isEqualTo("Smith vs Johnson Contract Dispute");
        assertThat(caseResponse.getStatus()).isEqualTo(CaseStatus.OPEN);

        // ============================================
        // STEP 3: Add Staff as Case Member
        // ============================================

        AddMemberRequest addMemberRequest = new AddMemberRequest();
        addMemberRequest.setIdentifier("staffjane");
        addMemberRequest.setRole(CaseMemberRole.STAFF);

        MvcResult memberResult = mockMvc.perform(post("/cases/{caseId}/members", caseId)
                        .header("Authorization", "Bearer " + lawyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addMemberRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        String memberResponseJson = memberResult.getResponse().getContentAsString();
        MemberResponse memberResponse = objectMapper.readValue(memberResponseJson, MemberResponse.class);

        assertThat(memberResponse.getUserId()).isEqualTo(staffId);
        assertThat(memberResponse.getRole()).isEqualTo(CaseMemberRole.STAFF);

        // ============================================
        // STEP 4: Create a Task (as Lawyer)
        // ============================================

        CreateTaskRequest taskRequest = new CreateTaskRequest();
        taskRequest.setTitle("Review Contract Documents");
        taskRequest.setDescription("Review and analyze the contract for compliance with local laws");
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

        assertThat(taskId).isNotNull();
        assertThat(taskResponse.getTitle()).isEqualTo("Review Contract Documents");
        assertThat(taskResponse.getType()).isEqualTo(TaskType.MANDATORY);
        assertThat(taskResponse.getStatus()).isEqualTo(TaskStatus.TODO);
        assertThat(taskResponse.getAssignedToId()).isEqualTo(staffId);

        // ============================================
        // STEP 5: Create Case Comment (as Lawyer)
        // ============================================

        CreateCommentRequest caseCommentRequest = new CreateCommentRequest();
        caseCommentRequest.setContent("This case requires immediate attention. The plaintiff has filed an urgent motion.");
        caseCommentRequest.setType(CommentType.CASE);
        caseCommentRequest.setCaseId(caseId);

        MvcResult caseCommentResult = mockMvc.perform(post("/comments")
                        .header("Authorization", "Bearer " + lawyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(caseCommentRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        String caseCommentResponseJson = caseCommentResult.getResponse().getContentAsString();
        CommentResponse caseCommentResponse = objectMapper.readValue(caseCommentResponseJson, CommentResponse.class);

        assertThat(caseCommentResponse.getId()).isNotNull();
        assertThat(caseCommentResponse.getContent()).contains("immediate attention");
        assertThat(caseCommentResponse.getType()).isEqualTo(CommentType.CASE);
        assertThat(caseCommentResponse.getCaseId()).isEqualTo(caseId);
    }

    // ============================================
    // USER MODULE TESTS
    // ============================================

    @Test
    @DisplayName("Integration: User can login and receive JWT token")
    void testUserLogin() throws Exception {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("lawyer@legalfirm.com");
        loginRequest.setPassword("SecurePass123!");

        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.token").exists())
                .andReturn();

        String responseJson = result.getResponse().getContentAsString();
        AuthResponse authResponse = objectMapper.readValue(responseJson, AuthResponse.class);

        assertThat(authResponse.getToken()).isNotNull();
        assertThat(authResponse.getUser().getEmail()).isEqualTo("lawyer@legalfirm.com");
    }

    @Test
    @DisplayName("Integration: User can get current user info from token")
    void testGetCurrentUser() throws Exception {
        mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer " + lawyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("lawyer@legalfirm.com"))
                .andExpect(jsonPath("$.username").value("lawyerjohn"))
                .andExpect(jsonPath("$.fullName").value("John Lawyer"))
                .andExpect(jsonPath("$.role").value("LAWYER"));
    }

    @Test
    @DisplayName("Integration: Check username availability")
    void testCheckUsernameAvailability() throws Exception {
        // Available username
        mockMvc.perform(get("/auth/check-username")
                        .param("username", "newuser"))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));

        // Taken username
        mockMvc.perform(get("/auth/check-username")
                        .param("username", "lawyerjohn"))
                .andExpect(status().isOk())
                .andExpect(content().string("false"));
    }

    // ============================================
    // CASE MODULE TESTS
    // ============================================

    @Test
    @DisplayName("Integration: Get case by ID")
    void testGetCaseById() throws Exception {
        mockMvc.perform(get("/cases/{caseId}", caseId)
                        .header("Authorization", "Bearer " + lawyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(caseId))
                .andExpect(jsonPath("$.title").value("Smith vs Johnson Contract Dispute"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.type").value("CIVIL"))
                .andExpect(jsonPath("$.ownerName").value("John Lawyer"));
    }

    @Test
    @DisplayName("Integration: Get all cases for current user")
    void testGetMyCases() throws Exception {
        mockMvc.perform(get("/cases/my-cases")
                        .header("Authorization", "Bearer " + lawyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdByMe[0].id").value(caseId))
                .andExpect(jsonPath("$.createdByMe[0].title").value("Smith vs Johnson Contract Dispute"))
                .andExpect(jsonPath("$.assignedToMe").isArray());
    }

    @Test
    @DisplayName("Integration: Get case members")
    void testGetCaseMembers() throws Exception {
        mockMvc.perform(get("/cases/{caseId}/members", caseId)
                        .header("Authorization", "Bearer " + lawyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].userFullName").value("John Lawyer"))
                .andExpect(jsonPath("$[0].role").value("LAWYER"))
                .andExpect(jsonPath("$[1].userFullName").value("Jane Staff"))
                .andExpect(jsonPath("$[1].role").value("STAFF"));
    }

    @Test
    @DisplayName("Integration: Update case priority")
    void testUpdateCasePriority() throws Exception {
        mockMvc.perform(patch("/cases/{caseId}/priority?priority=URGENT", caseId)
                        .header("Authorization", "Bearer " + lawyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priority").value("URGENT"));
    }

    @Test
    @DisplayName("Integration: Get case progress")
    void testGetCaseProgress() throws Exception {
        mockMvc.perform(get("/cases/{caseId}/progress", caseId)
                        .header("Authorization", "Bearer " + lawyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progressPercentage").exists())
                .andExpect(jsonPath("$.readyForInProgress").exists())
                .andExpect(jsonPath("$.readyForClosed").exists());
    }

    // ============================================
    // TASK MODULE TESTS
    // ============================================

    @Test
    @DisplayName("Integration: Get tasks by case")
    void testGetTasksByCase() throws Exception {
        mockMvc.perform(get("/tasks/case/{caseId}", caseId)
                        .header("Authorization", "Bearer " + lawyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(taskId))
                .andExpect(jsonPath("$[0].title").value("Review Contract Documents"))
                .andExpect(jsonPath("$[0].type").value("MANDATORY"))
                .andExpect(jsonPath("$[0].status").value("TODO"))
                .andExpect(jsonPath("$[0].assignedToName").value("Jane Staff"));
    }

    @Test
    @DisplayName("Integration: Get task by ID")
    void testGetTaskById() throws Exception {
        mockMvc.perform(get("/tasks/{taskId}", taskId)
                        .header("Authorization", "Bearer " + lawyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(taskId))
                .andExpect(jsonPath("$.title").value("Review Contract Documents"))
                .andExpect(jsonPath("$.assignedToId").value(staffId));
    }

    @Test
    @DisplayName("Integration: Staff updates task progress")
    void testUpdateTaskProgress() throws Exception {
        UpdateTaskProgressRequest progressRequest = new UpdateTaskProgressRequest();
        progressRequest.setProgress(50);

        mockMvc.perform(patch("/tasks/{taskId}/progress", taskId)
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(progressRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progress").value(50))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    @DisplayName("Integration: Get tasks assigned to current user")
    void testGetMyTasks() throws Exception {
        mockMvc.perform(get("/tasks/my-tasks")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(taskId))
                .andExpect(jsonPath("$[0].title").value("Review Contract Documents"))
                .andExpect(jsonPath("$[0].assignedToId").value(staffId));
    }

    @Test
    @DisplayName("Integration: Complete task workflow (Staff → REVIEW → Lawyer approves)")
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

        // Step 2: Lawyer approves task (moves to COMPLETED)
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
    @DisplayName("Integration: Get case comments")
    void testGetCaseComments() throws Exception {
        mockMvc.perform(get("/comments/case/{caseId}", caseId)
                        .header("Authorization", "Bearer " + lawyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").exists())
                .andExpect(jsonPath("$[0].content").value("This case requires immediate attention. The plaintiff has filed an urgent motion."))
                .andExpect(jsonPath("$[0].type").value("CASE"))
                .andExpect(jsonPath("$[0].authorName").value("John Lawyer"));
    }

    @Test
    @DisplayName("Integration: Create task comment")
    void testCreateTaskComment() throws Exception {
        CreateCommentRequest taskCommentRequest = new CreateCommentRequest();
        taskCommentRequest.setContent("The contract has several ambiguous clauses in section 5.3");
        taskCommentRequest.setType(CommentType.TASK);
        taskCommentRequest.setTaskId(taskId);

        MvcResult result = mockMvc.perform(post("/comments")
                        .header("Authorization", "Bearer " + lawyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(taskCommentRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        String responseJson = result.getResponse().getContentAsString();
        CommentResponse commentResponse = objectMapper.readValue(responseJson, CommentResponse.class);

        assertThat(commentResponse.getId()).isNotNull();
        assertThat(commentResponse.getContent()).contains("ambiguous clauses");
        assertThat(commentResponse.getType()).isEqualTo(CommentType.TASK);
        assertThat(commentResponse.getTaskId()).isEqualTo(taskId);
    }

    @Test
    @DisplayName("Integration: Create reply to comment")
    void testCreateReplyComment() throws Exception {
        // First, get the existing comment ID
        MvcResult getCommentsResult = mockMvc.perform(get("/comments/case/{caseId}", caseId)
                        .header("Authorization", "Bearer " + lawyerToken))
                .andExpect(status().isOk())
                .andReturn();

        String commentsJson = getCommentsResult.getResponse().getContentAsString();
        CommentResponse[] comments = objectMapper.readValue(commentsJson, CommentResponse[].class);
        Long parentCommentId = comments[0].getId();

        // Create a reply
        CreateCommentRequest replyRequest = new CreateCommentRequest();
        replyRequest.setContent("I agree. Let's schedule an urgent meeting to discuss this.");
        replyRequest.setType(CommentType.CASE);
        replyRequest.setCaseId(caseId);
        replyRequest.setParentCommentId(parentCommentId);

        MvcResult replyResult = mockMvc.perform(post("/comments")
                        .header("Authorization", "Bearer " + lawyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(replyRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        String replyResponseJson = replyResult.getResponse().getContentAsString();
        CommentResponse replyResponse = objectMapper.readValue(replyResponseJson, CommentResponse.class);

        assertThat(replyResponse.getParentCommentId()).isEqualTo(parentCommentId);
        assertThat(replyResponse.getContent()).contains("urgent meeting");
    }

    @Test
    @DisplayName("Integration: Update comment")
    void testUpdateComment() throws Exception {
        // Create a comment first
        CreateCommentRequest commentRequest = new CreateCommentRequest();
        commentRequest.setContent("Original comment content");
        commentRequest.setType(CommentType.CASE);
        commentRequest.setCaseId(caseId);

        MvcResult createResult = mockMvc.perform(post("/comments")
                        .header("Authorization", "Bearer " + lawyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(commentRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        String createResponseJson = createResult.getResponse().getContentAsString();
        CommentResponse createdComment = objectMapper.readValue(createResponseJson, CommentResponse.class);

        // Update the comment
        UpdateCommentRequest updateRequest = new UpdateCommentRequest();
        updateRequest.setContent("Updated comment content");

        mockMvc.perform(put("/comments/{commentId}", createdComment.getId())
                        .header("Authorization", "Bearer " + lawyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Updated comment content"));
    }

    @Test
    @DisplayName("Integration: Delete comment")
    void testDeleteComment() throws Exception {
        // Create a comment first
        CreateCommentRequest commentRequest = new CreateCommentRequest();
        commentRequest.setContent("Comment to be deleted");
        commentRequest.setType(CommentType.CASE);
        commentRequest.setCaseId(caseId);

        MvcResult createResult = mockMvc.perform(post("/comments")
                        .header("Authorization", "Bearer " + lawyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(commentRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        String createResponseJson = createResult.getResponse().getContentAsString();
        CommentResponse createdComment = objectMapper.readValue(createResponseJson, CommentResponse.class);

        // Delete the comment
        mockMvc.perform(delete("/comments/{commentId}", createdComment.getId())
                        .header("Authorization", "Bearer " + lawyerToken))
                .andExpect(status().isNoContent());

        // Verify comment is deleted (should return 404)
        mockMvc.perform(get("/comments/{commentId}", createdComment.getId())
                        .header("Authorization", "Bearer " + lawyerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Integration: Get all case comments (flat list)")
    void testGetAllCaseComments() throws Exception {
        mockMvc.perform(get("/comments/case/{caseId}/all", caseId)
                        .header("Authorization", "Bearer " + lawyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].content").value("This case requires immediate attention. The plaintiff has filed an urgent motion."));
    }

    // ============================================
    // CROSS-MODULE WORKFLOW TESTS
    // ============================================

    @Test
    @DisplayName("Integration: Complete case workflow (OPEN → IN_PROGRESS → CLOSED)")
    void testCompleteCaseWorkflow() throws Exception {
        // Step 1: Create another mandatory task (to satisfy IN_PROGRESS transition)
        CreateTaskRequest secondTask = new CreateTaskRequest();
        secondTask.setTitle("Prepare Legal Memorandum");
        secondTask.setDescription("Prepare a detailed legal memorandum on the case");
        secondTask.setType(TaskType.MANDATORY);
        secondTask.setPriority(TaskPriority.MEDIUM);
        secondTask.setDueDate(LocalDate.now().plusDays(21));
        secondTask.setAssignedToUserId(staffId);

        mockMvc.perform(post("/tasks/case/{caseId}", caseId)
                        .header("Authorization", "Bearer " + lawyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(secondTask)))
                .andExpect(status().isCreated());

        // Step 2: Move case to IN_PROGRESS (requires at least one task)
        UpdateCaseStatusRequest statusRequest = new UpdateCaseStatusRequest();
        statusRequest.setStatus(CaseStatus.IN_PROGRESS);

        mockMvc.perform(patch("/cases/{caseId}/status", caseId)
                        .header("Authorization", "Bearer " + lawyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        // Step 3: Complete all mandatory tasks
        // Complete first task
        UpdateTaskProgressRequest completeFirst = new UpdateTaskProgressRequest();
        completeFirst.setProgress(100);
        mockMvc.perform(patch("/tasks/{taskId}/progress", taskId)
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(completeFirst)))
                .andExpect(status().isOk());

        // Approve first task
        UpdateTaskStatusRequest approveFirst = new UpdateTaskStatusRequest();
        approveFirst.setStatus(TaskStatus.COMPLETED);
        mockMvc.perform(patch("/tasks/{taskId}/status", taskId)
                        .header("Authorization", "Bearer " + lawyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(approveFirst)))
                .andExpect(status().isOk());

        // Get second task ID
        MvcResult tasksResult = mockMvc.perform(get("/tasks/case/{caseId}", caseId)
                        .header("Authorization", "Bearer " + lawyerToken))
                .andExpect(status().isOk())
                .andReturn();

        String tasksJson = tasksResult.getResponse().getContentAsString();
        TaskResponse[] tasks = objectMapper.readValue(tasksJson, TaskResponse[].class);
        Long secondTaskId = tasks[1].getId();

        // Complete second task
        mockMvc.perform(patch("/tasks/{taskId}/progress", secondTaskId)
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(completeFirst)))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/tasks/{taskId}/status", secondTaskId)
                        .header("Authorization", "Bearer " + lawyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(approveFirst)))
                .andExpect(status().isOk());

        // Step 4: Move case to CLOSED (all mandatory tasks completed)
        statusRequest.setStatus(CaseStatus.CLOSED);
        mockMvc.perform(patch("/cases/{caseId}/status", caseId)
                        .header("Authorization", "Bearer " + lawyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.locked").value(true));
    }

    @Test
    @DisplayName("Integration: Search users for case member addition")
    void testSearchUsers() throws Exception {
        // Search by username
        mockMvc.perform(get("/users/search?q=lawyerjohn")
                        .header("Authorization", "Bearer " + lawyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("lawyerjohn"))
                .andExpect(jsonPath("$[0].email").value("lawyer@legalfirm.com"));

        // Search by email
        mockMvc.perform(get("/users/search?q=staff@legalfirm.com")
                        .header("Authorization", "Bearer " + lawyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("staffjane"));

        // Search by full name
        mockMvc.perform(get("/users/search?q=John Lawyer")
                        .header("Authorization", "Bearer " + lawyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fullName").value("John Lawyer"));
    }

    @Test
    @DisplayName("Integration: Get unread notifications for user")
    void testGetUnreadNotifications() throws Exception {
        // This will verify the notification system is integrated
        mockMvc.perform(get("/notifications/unread/count")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").exists());
    }
}