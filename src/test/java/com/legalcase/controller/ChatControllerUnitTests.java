package com.legalcase.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.legalcase.dto.request.MarkMessagesReadRequest;
import com.legalcase.dto.request.SendMessageRequest;
import com.legalcase.dto.response.ChatMessageResponse;
import com.legalcase.dto.response.UnreadCountResponse;
import com.legalcase.entity.ChatMessage;
import com.legalcase.entity.LegalCase;
import com.legalcase.entity.User;
import com.legalcase.enums.MessageType;
import com.legalcase.enums.Role;
import com.legalcase.security.JwtUtils;
import com.legalcase.service.ChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Chat Controller Unit Tests")
class ChatControllerUnitTests {

    @Mock
    private ChatService chatService;

    @Mock
    private JwtUtils jwtUtils;

    @InjectMocks
    private ChatController chatController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private String mockToken = "Bearer mock.jwt.token";
    private Long mockUserId = 1L;

    private ChatMessage mockChatMessage;
    private ChatMessageResponse mockChatMessageResponse;
    private LegalCase mockLegalCase;
    private User mockSender;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(chatController).build();

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        // Setup mock sender
        mockSender = new User();
        mockSender.setId(1L);
        mockSender.setUsername("lawyerjohn");
        mockSender.setFullName("John Lawyer");
        mockSender.setEmail("john@legalfirm.com");
        mockSender.setRole(Role.LAWYER);

        // Setup mock case
        mockLegalCase = new LegalCase();
        mockLegalCase.setId(1L);
        mockLegalCase.setCaseNumber("CASE-2026-00001");
        mockLegalCase.setTitle("Test Case");

        // Setup mock chat message
        mockChatMessage = new ChatMessage();
        mockChatMessage.setId(1L);
        mockChatMessage.setContent("Hello world!");
        mockChatMessage.setType(MessageType.TEXT);
        mockChatMessage.setLegalCase(mockLegalCase);
        mockChatMessage.setSender(mockSender);
        mockChatMessage.setSentAt(LocalDateTime.now());
        mockChatMessage.setRead(false);

        // Setup mock response DTO
        mockChatMessageResponse = ChatMessageResponse.builder()
                .id(1L)
                .content("Hello world!")
                .type(MessageType.TEXT)
                .caseId(1L)
                .caseNumber("CASE-2026-00001")
                .caseTitle("Test Case")
                .senderId(1L)
                .senderUsername("lawyerjohn")
                .senderName("John Lawyer")
                .sentAt(LocalDateTime.now())
                .isRead(false)
                .build();
    }

    // ============================================
    // SEND MESSAGE TESTS
    // ============================================

    @Test
    @DisplayName("POST /api/chat/messages - Should return 201 when message sent")
    void sendMessage_Success_Returns201() throws Exception {
        when(jwtUtils.getUserIdFromToken(anyString())).thenReturn(mockUserId);
        when(chatService.sendMessage(
                anyString(),
                any(MessageType.class),
                anyLong(),
                anyLong(),
                anyString(),
                anyString(),
                anyLong(),
                anyList(),
                anyList()
        )).thenReturn(mockChatMessage);

        SendMessageRequest request = new SendMessageRequest();
        request.setContent("Hello world!");
        request.setType(MessageType.TEXT);
        request.setCaseId(1L);

        mockMvc.perform(post("/chat/messages")
                        .header("Authorization", mockToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.content").value("Hello world!"))
                .andExpect(jsonPath("$.type").value("TEXT"))
                .andExpect(jsonPath("$.senderId").value(1))
                .andExpect(jsonPath("$.senderUsername").value("lawyerjohn"));
    }

    @Test
    @DisplayName("POST /api/chat/messages - Should return 400 when content is empty")
    void sendMessage_EmptyContent_Returns400() throws Exception {
        SendMessageRequest request = new SendMessageRequest();
        request.setContent("");
        request.setType(MessageType.TEXT);
        request.setCaseId(1L);

        mockMvc.perform(post("/chat/messages")
                        .header("Authorization", mockToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ============================================
    // GET MESSAGES BY CASE TESTS (Paginated - Now returns DTOs)
    // ============================================

    @Test
    @DisplayName("GET /api/chat/cases/{caseId}/messages - Should return paginated messages as DTOs")
    void getMessagesByCase_Success_Returns200() throws Exception {
        when(jwtUtils.getUserIdFromToken(anyString())).thenReturn(mockUserId);

        Page<ChatMessageResponse> messagesPage = new PageImpl<>(
                Arrays.asList(mockChatMessageResponse),
                PageRequest.of(0, 20),
                1
        );

        when(chatService.getMessagesByCase(eq(1L), eq(0), eq(20), eq(mockUserId)))
                .thenReturn(messagesPage);

        mockMvc.perform(get("/chat/cases/1/messages?page=0&size=20")
                        .header("Authorization", mockToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].content").value("Hello world!"))
                .andExpect(jsonPath("$.content[0].senderName").value("John Lawyer"));
    }

    // ============================================
    // GET ALL MESSAGES BY CASE TESTS (Returns DTOs)
    // ============================================

    @Test
    @DisplayName("GET /api/chat/cases/{caseId}/messages/all - Should return all messages as DTOs")
    void getAllMessagesByCase_Success_Returns200() throws Exception {
        when(jwtUtils.getUserIdFromToken(anyString())).thenReturn(mockUserId);

        List<ChatMessageResponse> messages = Arrays.asList(mockChatMessageResponse);
        when(chatService.getAllMessagesByCase(eq(1L), eq(mockUserId)))
                .thenReturn(messages);

        mockMvc.perform(get("/chat/cases/1/messages/all")
                        .header("Authorization", mockToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].content").value("Hello world!"))
                .andExpect(jsonPath("$[0].senderName").value("John Lawyer"));
    }

    // ============================================
    // GET UNREAD MESSAGES TESTS (Returns DTOs)
    // ============================================

    @Test
    @DisplayName("GET /api/chat/cases/{caseId}/unread - Should return unread messages as DTOs")
    void getUnreadMessagesByCase_Success_Returns200() throws Exception {
        when(jwtUtils.getUserIdFromToken(anyString())).thenReturn(mockUserId);

        List<ChatMessageResponse> unreadMessages = Arrays.asList(mockChatMessageResponse);
        when(chatService.getUnreadMessagesByCase(eq(1L), eq(mockUserId)))
                .thenReturn(unreadMessages);

        mockMvc.perform(get("/chat/cases/1/unread")
                        .header("Authorization", mockToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].content").value("Hello world!"));
    }

    // ============================================
    // GET UNREAD COUNTS TESTS
    // ============================================

    @Test
    @DisplayName("GET /api/chat/unread/counts - Should return unread counts across all cases")
    void getUnreadCounts_Success_Returns200() throws Exception {
        when(jwtUtils.getUserIdFromToken(anyString())).thenReturn(mockUserId);

        Map<Long, Long> unreadByCase = new HashMap<>();
        unreadByCase.put(1L, 3L);
        unreadByCase.put(2L, 2L);

        UnreadCountResponse mockResponse = UnreadCountResponse.builder()
                .totalUnread(5L)
                .unreadByCase(unreadByCase)
                .build();

        when(chatService.getUnreadCounts(mockUserId)).thenReturn(mockResponse);

        mockMvc.perform(get("/chat/unread/counts")
                        .header("Authorization", mockToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUnread").value(5));
    }

    // ============================================
    // MARK MESSAGES AS READ TESTS
    // ============================================

    @Test
    @DisplayName("PUT /api/chat/messages/read - Should return 200 when messages marked as read")
    void markMessagesAsRead_Success_Returns200() throws Exception {
        when(jwtUtils.getUserIdFromToken(anyString())).thenReturn(mockUserId);

        MarkMessagesReadRequest request = new MarkMessagesReadRequest();
        request.setMessageIds(Arrays.asList(1L, 2L, 3L));

        mockMvc.perform(put("/chat/messages/read")
                        .header("Authorization", mockToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(chatService, times(1)).markMessagesAsRead(anyList(), eq(mockUserId));
    }

    // ============================================
    // MARK ALL MESSAGES AS READ IN CASE TESTS
    // ============================================

    @Test
    @DisplayName("PUT /api/chat/cases/{caseId}/read - Should return 200 when all messages marked as read")
    void markAllMessagesAsReadInCase_Success_Returns200() throws Exception {
        when(jwtUtils.getUserIdFromToken(anyString())).thenReturn(mockUserId);

        mockMvc.perform(put("/chat/cases/1/read")
                        .header("Authorization", mockToken))
                .andExpect(status().isOk());

        verify(chatService, times(1)).markAllMessagesAsReadInCase(eq(1L), eq(mockUserId));
    }
}