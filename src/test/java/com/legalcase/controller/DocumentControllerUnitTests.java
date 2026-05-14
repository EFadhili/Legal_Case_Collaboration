package com.legalcase.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legalcase.dto.response.DocumentResponse;
import com.legalcase.entity.Document;
import com.legalcase.entity.LegalCase;
import com.legalcase.entity.User;
import com.legalcase.enums.DocumentStatus;
import com.legalcase.enums.Role;
import com.legalcase.enums.TextExtractionStatus;
import com.legalcase.security.JwtUtils;
import com.legalcase.service.DocumentService;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Document Controller Unit Tests")
class DocumentControllerUnitTests {

    @Mock
    private DocumentService documentService;

    @Mock
    private JwtUtils jwtUtils;

    @InjectMocks
    private DocumentController documentController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private String mockToken = "Bearer mock.jwt.token";
    private Long mockUserId = 1L;

    private Document mockDocument;
    private LegalCase mockLegalCase;
    private User mockUser;
    private MockMultipartFile mockFile;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(documentController).build();
        objectMapper = new ObjectMapper();

        // Setup mock user
        mockUser = new User();
        mockUser.setId(1L);
        mockUser.setUsername("lawyerjohn");
        mockUser.setFullName("John Lawyer");
        mockUser.setEmail("john@legalfirm.com");
        mockUser.setRole(Role.LAWYER);

        // Setup mock case
        mockLegalCase = new LegalCase();
        mockLegalCase.setId(1L);
        mockLegalCase.setCaseNumber("CASE-2026-00001");
        mockLegalCase.setTitle("Test Case");

        // Setup mock document
        mockDocument = new Document();
        mockDocument.setId(1L);
        mockDocument.setFileName("abc123_contract.pdf");
        mockDocument.setOriginalFileName("contract.pdf");
        mockDocument.setFileType("application/pdf");
        mockDocument.setFileExtension("pdf");
        mockDocument.setFileSize(1024L);
        mockDocument.setStoragePath("D:\\dataset_copy\\Contracts\\contract_002.pdf");
        mockDocument.setCaseId(1L);
        mockDocument.setUploadedById(1L);
        mockDocument.setUploadedAt(LocalDateTime.now());
        mockDocument.setTextExtractionStatus(TextExtractionStatus.COMPLETED);
        mockDocument.setExtractedText("This is the extracted text from the contract...");
        mockDocument.setStatus(DocumentStatus.ACTIVE);
        mockDocument.setVersion(1);
        mockDocument.setLatest(true);

        // Setup mock file
        mockFile = new MockMultipartFile(
                "file",
                "contract.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "Test PDF content".getBytes()
        );
    }

    // ============================================
    // UPLOAD TO CASE TESTS
    // ============================================

    @Test
    @DisplayName("POST /api/documents/case/{caseId} - Should return 201 when document uploaded to case")
    void uploadToCase_Success_Returns201() throws Exception {
        when(jwtUtils.getUserIdFromToken(anyString())).thenReturn(mockUserId);
        when(documentService.uploadToCase(any(), eq(1L), eq(mockUserId), any(), any()))
                .thenReturn(mockDocument);
        when(documentService.getDownloadUrl(any())).thenReturn("https://s3.amazonaws.com/bucket/contract.pdf");
        when(documentService.getUserFullName(mockUserId)).thenReturn("John Lawyer");
        when(documentService.getUserUsername(mockUserId)).thenReturn("lawyerjohn");

        mockMvc.perform(multipart("/documents/case/1")
                        .file(mockFile)
                        .param("description", "Test contract")
                        .param("tags", "contract,legal")
                        .header("Authorization", mockToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.originalFileName").value("contract.pdf"))
                .andExpect(jsonPath("$.fileExtension").value("pdf"))
                .andExpect(jsonPath("$.caseId").value(1))
                .andExpect(jsonPath("$.uploadedBy").value("John Lawyer"))
                .andExpect(jsonPath("$.textExtractionStatus").value("COMPLETED"));

        verify(documentService, times(1)).uploadToCase(any(), eq(1L), eq(mockUserId), any(), any());
    }

    // ============================================
    // UPLOAD TO TASK TESTS
    // ============================================

    @Test
    @DisplayName("POST /api/documents/task/{taskId} - Should return 201 when document uploaded to task")
    void uploadToTask_Success_Returns201() throws Exception {
        when(jwtUtils.getUserIdFromToken(anyString())).thenReturn(mockUserId);
        when(documentService.uploadToTask(any(), eq(1L), eq(mockUserId), any(), any()))
                .thenReturn(mockDocument);
        when(documentService.getDownloadUrl(any())).thenReturn("https://s3.amazonaws.com/bucket/contract.pdf");
        when(documentService.getUserFullName(mockUserId)).thenReturn("John Lawyer");
        when(documentService.getUserUsername(mockUserId)).thenReturn("lawyerjohn");

        mockMvc.perform(multipart("/documents/task/1")
                        .file(mockFile)
                        .param("description", "Task document")
                        .param("tags", "draft")
                        .header("Authorization", mockToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.originalFileName").value("contract.pdf"))
                .andExpect(jsonPath("$.fileExtension").value("pdf"));

        verify(documentService, times(1)).uploadToTask(any(), eq(1L), eq(mockUserId), any(), any());
    }

    // ============================================
    // GET CASE DOCUMENTS TESTS
    // ============================================

    @Test
    @DisplayName("GET /api/documents/case/{caseId} - Should return list of case documents")
    void getCaseDocuments_Success_Returns200() throws Exception {
        List<Document> documents = Arrays.asList(mockDocument);
        when(documentService.getCaseDocuments(1L)).thenReturn(documents);
        when(documentService.getDownloadUrl(any())).thenReturn("https://s3.amazonaws.com/bucket/contract.pdf");
        when(documentService.getUserFullName(anyLong())).thenReturn("John Lawyer");
        when(documentService.getUserUsername(anyLong())).thenReturn("lawyerjohn");

        mockMvc.perform(get("/documents/case/1")
                        .header("Authorization", mockToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].originalFileName").value("contract.pdf"))
                .andExpect(jsonPath("$[0].caseId").value(1));

        verify(documentService, times(1)).getCaseDocuments(1L);
    }

    // ============================================
    // GET TASK DOCUMENTS TESTS
    // ============================================

    @Test
    @DisplayName("GET /api/documents/task/{taskId} - Should return list of task documents")
    void getTaskDocuments_Success_Returns200() throws Exception {
        List<Document> documents = Arrays.asList(mockDocument);
        when(documentService.getTaskDocuments(1L)).thenReturn(documents);
        when(documentService.getDownloadUrl(any())).thenReturn("https://s3.amazonaws.com/bucket/contract.pdf");
        when(documentService.getUserFullName(anyLong())).thenReturn("John Lawyer");
        when(documentService.getUserUsername(anyLong())).thenReturn("lawyerjohn");

        mockMvc.perform(get("/documents/task/1")
                        .header("Authorization", mockToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].originalFileName").value("contract.pdf"));

        verify(documentService, times(1)).getTaskDocuments(1L);
    }

    // ============================================
    // GET PAGINATED CASE DOCUMENTS TESTS
    // ============================================

    @Test
    @DisplayName("GET /api/documents/case/{caseId}/paginated - Should return paginated documents")
    void getCaseDocumentsPaginated_Success_Returns200() throws Exception {
        Page<Document> documentsPage = new PageImpl<>(
                Arrays.asList(mockDocument),
                PageRequest.of(0, 20),
                1
        );

        when(documentService.getCaseDocumentsPaginated(eq(1L), any(PageRequest.class)))
                .thenReturn(documentsPage);
        when(documentService.getDownloadUrl(any())).thenReturn("https://s3.amazonaws.com/bucket/contract.pdf");
        when(documentService.getUserFullName(anyLong())).thenReturn("John Lawyer");
        when(documentService.getUserUsername(anyLong())).thenReturn("lawyerjohn");

        mockMvc.perform(get("/documents/case/1/paginated?page=0&size=20")
                        .header("Authorization", mockToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.totalElements").value(1));

        verify(documentService, times(1)).getCaseDocumentsPaginated(eq(1L), any(PageRequest.class));
    }

    // ============================================
    // GET DOCUMENT BY ID TESTS
    // ============================================

    @Test
    @DisplayName("GET /api/documents/{id} - Should return document metadata")
    void getDocument_Success_Returns200() throws Exception {
        when(documentService.findById(1L)).thenReturn(mockDocument);
        when(documentService.getDownloadUrl(any())).thenReturn("https://s3.amazonaws.com/bucket/contract.pdf");
        when(documentService.getUserFullName(anyLong())).thenReturn("John Lawyer");
        when(documentService.getUserUsername(anyLong())).thenReturn("lawyerjohn");

        mockMvc.perform(get("/documents/1")
                        .header("Authorization", mockToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.originalFileName").value("contract.pdf"))
                .andExpect(jsonPath("$.fileSize").value(1024));

        verify(documentService, times(1)).findById(1L);
    }

    @Test
    @DisplayName("GET /api/documents/{id} - Should return 404 when document not found")
    void getDocument_NotFound_Returns404() throws Exception {
        when(documentService.findById(999L))
                .thenThrow(new RuntimeException("Document not found"));

        mockMvc.perform(get("/documents/999")
                        .header("Authorization", mockToken))
                .andExpect(status().isNotFound());
    }

    // ============================================
    // GET EXTRACTED TEXT TESTS
    // ============================================

    @Test
    @DisplayName("GET /api/documents/{id}/text - Should return extracted text")
    void getExtractedText_Success_Returns200() throws Exception {
        when(documentService.getExtractedText(1L)).thenReturn("This is the extracted text from the contract...");

        mockMvc.perform(get("/documents/1/text")
                        .header("Authorization", mockToken))
                .andExpect(status().isOk())
                .andExpect(content().string("This is the extracted text from the contract..."));

        verify(documentService, times(1)).getExtractedText(1L);
    }

    // ============================================
    // UPDATE METADATA TESTS
    // ============================================

    @Test
    @DisplayName("PUT /api/documents/{id} - Should return 200 when metadata updated")
    void updateMetadata_Success_Returns200() throws Exception {
        mockDocument.setDescription("Updated description");
        mockDocument.setTags("updated, tags");

        when(documentService.updateMetadata(eq(1L), any(), any()))
                .thenReturn(mockDocument);
        when(documentService.getDownloadUrl(any())).thenReturn("https://s3.amazonaws.com/bucket/contract.pdf");
        when(documentService.getUserFullName(anyLong())).thenReturn("John Lawyer");
        when(documentService.getUserUsername(anyLong())).thenReturn("lawyerjohn");

        String requestBody = "{\"description\": \"Updated description\", \"tags\": \"updated, tags\"}";

        mockMvc.perform(put("/documents/1")
                        .header("Authorization", mockToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Updated description"))
                .andExpect(jsonPath("$.tags").value("updated, tags"));

        verify(documentService, times(1)).updateMetadata(eq(1L), any(), any());
    }

    // ============================================
    // DELETE DOCUMENT TESTS
    // ============================================

    @Test
    @DisplayName("DELETE /api/documents/{id} - Should return 204 when document soft deleted")
    void deleteDocument_Success_Returns204() throws Exception {
        mockMvc.perform(delete("/documents/1")
                        .header("Authorization", mockToken))
                .andExpect(status().isNoContent());

        verify(documentService, times(1)).softDelete(1L);
    }

    // ============================================
    // RESTORE DOCUMENT TESTS
    // ============================================

    @Test
    @DisplayName("POST /api/documents/{id}/restore - Should return 200 when document restored")
    void restoreDocument_Success_Returns200() throws Exception {
        when(documentService.findById(1L)).thenReturn(mockDocument);
        when(documentService.getDownloadUrl(any())).thenReturn("https://s3.amazonaws.com/bucket/contract.pdf");
        when(documentService.getUserFullName(anyLong())).thenReturn("John Lawyer");
        when(documentService.getUserUsername(anyLong())).thenReturn("lawyerjohn");

        mockMvc.perform(post("/documents/1/restore")
                        .header("Authorization", mockToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));

        verify(documentService, times(1)).restore(1L);
    }
}