package com.legalcase.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.legalcase.dto.request.CreateCommentRequest;
import com.legalcase.dto.request.UpdateCommentRequest;
import com.legalcase.entity.Comment;
import com.legalcase.entity.LegalCase;
import com.legalcase.entity.Task;
import com.legalcase.entity.User;
import com.legalcase.enums.*;
import com.legalcase.security.JwtUtils;
import com.legalcase.service.CommentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Comment Controller Unit Tests")
class CommentControllerUnitTests {

    @Mock
    private CommentService commentService;

    @Mock
    private JwtUtils jwtUtils;

    @InjectMocks
    private CommentController commentController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private String mockToken = "Bearer mock.jwt.token";
    private Long mockUserId = 1L;

    // Test data
    private Comment mockCaseComment;
    private Comment mockTaskComment;
    private Comment mockReplyComment;
    private LegalCase mockLegalCase;
    private Task mockTask;
    private User mockAuthor;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(commentController).build();

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        // Setup mock author
        mockAuthor = new User();
        mockAuthor.setId(1L);
        mockAuthor.setUsername("lawyerjohn");
        mockAuthor.setFullName("John Lawyer");
        mockAuthor.setEmail("john@legalfirm.com");
        mockAuthor.setRole(Role.LAWYER);

        // Setup mock case
        mockLegalCase = new LegalCase();
        mockLegalCase.setId(1L);
        mockLegalCase.setCaseNumber("CASE-2026-00001");
        mockLegalCase.setTitle("Test Case");
        mockLegalCase.setStatus(CaseStatus.OPEN);

        // Setup mock task
        mockTask = new Task();
        mockTask.setId(1L);
        mockTask.setTitle("Test Task");
        mockTask.setStatus(TaskStatus.TODO);
        mockTask.setLegalCase(mockLegalCase);

        // Setup mock case comment
        mockCaseComment = new Comment();
        mockCaseComment.setId(1L);
        mockCaseComment.setContent("This is a case comment");
        mockCaseComment.setType(CommentType.CASE);
        mockCaseComment.setAuthor(mockAuthor);
        mockCaseComment.setLegalCase(mockLegalCase);
        mockCaseComment.setCreatedAt(LocalDateTime.now());
        mockCaseComment.setUpdatedAt(LocalDateTime.now());
        mockCaseComment.setReplies(new ArrayList<>());

        // Setup mock task comment
        mockTaskComment = new Comment();
        mockTaskComment.setId(2L);
        mockTaskComment.setContent("This is a task comment");
        mockTaskComment.setType(CommentType.TASK);
        mockTaskComment.setAuthor(mockAuthor);
        mockTaskComment.setTask(mockTask);
        mockTaskComment.setCreatedAt(LocalDateTime.now());
        mockTaskComment.setUpdatedAt(LocalDateTime.now());
        mockTaskComment.setReplies(new ArrayList<>());

        // Setup mock reply comment
        mockReplyComment = new Comment();
        mockReplyComment.setId(3L);
        mockReplyComment.setContent("This is a reply");
        mockReplyComment.setType(CommentType.CASE);
        mockReplyComment.setAuthor(mockAuthor);
        mockReplyComment.setLegalCase(mockLegalCase);
        mockReplyComment.setParentComment(mockCaseComment);
        mockReplyComment.setCreatedAt(LocalDateTime.now());
        mockReplyComment.setUpdatedAt(LocalDateTime.now());
        mockReplyComment.setReplies(new ArrayList<>());
    }

    // ============================================
    // CREATE CASE COMMENT TESTS
    // ============================================

    @Test
    @DisplayName("POST /api/comments - Should return 201 when creating a case comment (type provided)")
    void createCaseComment_Success_Returns201() throws Exception {
        when(jwtUtils.getUserIdFromToken(anyString())).thenReturn(mockUserId);

        // Same format as reply test - use exact matching with eq() and isNull()
        when(commentService.createComment(
                eq("This is a case comment"),
                eq(CommentType.CASE),   // type is CASE
                eq(1L),                 // caseId
                isNull(),               // taskId is null
                eq(mockUserId),
                isNull(),               // parentCommentId is null
                isNull()                // mentionedUsernames is null
        )).thenReturn(mockCaseComment);

        CreateCommentRequest request = new CreateCommentRequest();
        request.setContent("This is a case comment");
        request.setType(CommentType.CASE);
        request.setCaseId(1L);

        mockMvc.perform(post("/comments")
                        .header("Authorization", mockToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.content").value("This is a case comment"))
                .andExpect(jsonPath("$.type").value("CASE"));
    }


    // ============================================
    // CREATE TASK COMMENT TESTS
    // ============================================

    @Test
    @DisplayName("POST /api/comments - Should return 201 when creating a task comment (type provided)")
    void createTaskComment_Success_Returns201() throws Exception {
        when(jwtUtils.getUserIdFromToken(anyString())).thenReturn(mockUserId);

        // Same format as reply test - use exact matching with eq() and isNull()
        when(commentService.createComment(
                eq("This is a task comment"),
                eq(CommentType.TASK),   // type is TASK
                isNull(),               // caseId is null
                eq(1L),                 // taskId
                eq(mockUserId),
                isNull(),               // parentCommentId is null
                isNull()                // mentionedUsernames is null
        )).thenReturn(mockTaskComment);

        CreateCommentRequest request = new CreateCommentRequest();
        request.setContent("This is a task comment");
        request.setType(CommentType.TASK);
        request.setTaskId(1L);

        mockMvc.perform(post("/comments")
                        .header("Authorization", mockToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.content").value("This is a task comment"))
                .andExpect(jsonPath("$.type").value("TASK"));
    }


    // ============================================
    // CREATE REPLY COMMENT TESTS (type NOT provided - inherits from parent)
    // ============================================

    @Test
    @DisplayName("POST /api/comments - Should return 201 when creating a reply (type not provided, inherits from parent)")
    void createReply_Success_Returns201() throws Exception {
        when(jwtUtils.getUserIdFromToken(anyString())).thenReturn(mockUserId);

        // CRITICAL: Mock the service call to return the reply comment
        when(commentService.createComment(
                eq("This is a reply"),
                isNull(),           // type is null for replies
                isNull(),           // caseId is null
                isNull(),           // taskId is null
                eq(mockUserId),
                eq(1L),             // parentCommentId
                isNull()            // mentionedUsernames
        )).thenReturn(mockReplyComment);

        CreateCommentRequest request = new CreateCommentRequest();
        request.setContent("This is a reply");
        request.setParentCommentId(1L);

        mockMvc.perform(post("/comments")
                        .header("Authorization", mockToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.content").value("This is a reply"))
                .andExpect(jsonPath("$.parentCommentId").value(1));
    }

    // ============================================
    // CREATE COMMENT WITH MENTIONS TESTS
    // ============================================

    // ============================================
// CREATE COMMENT WITH MENTIONS TESTS
// ============================================

    @Test
    @DisplayName("POST /api/comments - Should handle @mentions in comment")
    void createCommentWithMentions_Success_Returns201() throws Exception {
        Comment commentWithMentions = mockCaseComment;
        commentWithMentions.setContent("@staffjane @admin please review this case");

        when(jwtUtils.getUserIdFromToken(anyString())).thenReturn(mockUserId);

        // Use exact matching pattern like the other tests
        when(commentService.createComment(
                eq("@staffjane @admin please review this case"),
                eq(CommentType.CASE),
                eq(1L),
                isNull(),
                eq(mockUserId),
                isNull(),
                eq(Arrays.asList("staffjane", "admin"))  // exact list match
        )).thenReturn(commentWithMentions);

        CreateCommentRequest request = new CreateCommentRequest();
        request.setContent("@staffjane @admin please review this case");
        request.setType(CommentType.CASE);
        request.setCaseId(1L);
        request.setMentionedUsernames(Arrays.asList("staffjane", "admin"));

        mockMvc.perform(post("/comments")
                        .header("Authorization", mockToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.content").value("@staffjane @admin please review this case"));
    }

    // ============================================
    // GET COMMENTS BY CASE TESTS
    // ============================================

    @Test
    @DisplayName("GET /api/comments/case/{caseId} - Should return list of case comments")
    void getCommentsByCase_Success_Returns200() throws Exception {
        List<Comment> comments = Arrays.asList(mockCaseComment);
        when(commentService.getRootCommentsByCase(1L)).thenReturn(comments);

        mockMvc.perform(get("/comments/case/1")
                        .header("Authorization", mockToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].content").value("This is a case comment"))
                .andExpect(jsonPath("$[0].type").value("CASE"));
    }

    // ============================================
    // GET ALL COMMENTS BY CASE TESTS (Flat List)
    // ============================================

    @Test
    @DisplayName("GET /api/comments/case/{caseId}/all - Should return flat list of all case comments")
    void getAllCommentsByCase_Success_Returns200() throws Exception {
        List<Comment> comments = Arrays.asList(mockCaseComment, mockReplyComment);
        when(commentService.getAllCommentsByCase(1L)).thenReturn(comments);

        mockMvc.perform(get("/comments/case/1/all")
                        .header("Authorization", mockToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].content").value("This is a case comment"))
                .andExpect(jsonPath("$[1].id").value(3))
                .andExpect(jsonPath("$[1].content").value("This is a reply"));
    }

    // ============================================
    // GET COMMENTS BY TASK TESTS
    // ============================================

    @Test
    @DisplayName("GET /api/comments/task/{taskId} - Should return list of task comments")
    void getCommentsByTask_Success_Returns200() throws Exception {
        List<Comment> comments = Arrays.asList(mockTaskComment);
        when(commentService.getRootCommentsByTask(1L)).thenReturn(comments);

        mockMvc.perform(get("/comments/task/1")
                        .header("Authorization", mockToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(2))
                .andExpect(jsonPath("$[0].content").value("This is a task comment"))
                .andExpect(jsonPath("$[0].type").value("TASK"));
    }

    // ============================================
    // GET ALL COMMENTS BY TASK TESTS (Flat List)
    // ============================================

    @Test
    @DisplayName("GET /api/comments/task/{taskId}/all - Should return flat list of all task comments")
    void getAllCommentsByTask_Success_Returns200() throws Exception {
        List<Comment> comments = Arrays.asList(mockTaskComment);
        when(commentService.getAllCommentsByTask(1L)).thenReturn(comments);

        mockMvc.perform(get("/comments/task/1/all")
                        .header("Authorization", mockToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(2))
                .andExpect(jsonPath("$[0].content").value("This is a task comment"));
    }

    // ============================================
    // GET COMMENT BY ID TESTS
    // ============================================

    @Test
    @DisplayName("GET /api/comments/{id} - Should return 200 when comment exists")
    void getCommentById_Success_Returns200() throws Exception {
        when(commentService.findById(1L)).thenReturn(mockCaseComment);

        mockMvc.perform(get("/comments/1")
                        .header("Authorization", mockToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.content").value("This is a case comment"));
    }

    @Test
    @DisplayName("GET /api/comments/{id} - Should return 404 when comment doesn't exist")
    void getCommentById_NotFound_Returns404() throws Exception {
        when(commentService.findById(999L))
                .thenThrow(new RuntimeException("Comment not found with ID: 999"));

        mockMvc.perform(get("/comments/999")
                        .header("Authorization", mockToken))
                .andExpect(status().isNotFound());
    }

    // ============================================
    // UPDATE COMMENT TESTS
    // ============================================

    @Test
    @DisplayName("PUT /api/comments/{id} - Should return 200 when comment updated")
    void updateComment_Success_Returns200() throws Exception {
        Comment updatedComment = mockCaseComment;
        updatedComment.setContent("Updated comment content");

        when(jwtUtils.getUserIdFromToken(anyString())).thenReturn(mockUserId);
        when(commentService.updateComment(eq(1L), eq("Updated comment content"), eq(mockUserId)))
                .thenReturn(updatedComment);

        UpdateCommentRequest request = new UpdateCommentRequest();
        request.setContent("Updated comment content");

        mockMvc.perform(put("/comments/1")
                        .header("Authorization", mockToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.content").value("Updated comment content"));
    }

    // ============================================
    // DELETE COMMENT TESTS
    // ============================================

    @Test
    @DisplayName("DELETE /api/comments/{id} - Should return 204 when comment deleted")
    void deleteComment_Success_Returns204() throws Exception {
        when(jwtUtils.getUserIdFromToken(anyString())).thenReturn(mockUserId);

        mockMvc.perform(delete("/comments/1")
                        .header("Authorization", mockToken))
                .andExpect(status().isNoContent());

        verify(commentService, times(1)).deleteComment(eq(1L), eq(mockUserId), eq(false));
    }

    // ============================================
    // GET MENTIONS FOR CURRENT USER TESTS
    // ============================================

    @Test
    @DisplayName("GET /api/comments/mentions/me - Should return comments mentioning current user")
    void getCommentsMentioningMe_Success_Returns200() throws Exception {
        List<Comment> comments = Arrays.asList(mockCaseComment);
        when(jwtUtils.getUserIdFromToken(anyString())).thenReturn(mockUserId);
        when(commentService.getCommentsMentioningUser(mockUserId)).thenReturn(comments);

        mockMvc.perform(get("/comments/mentions/me")
                        .header("Authorization", mockToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));
    }
}