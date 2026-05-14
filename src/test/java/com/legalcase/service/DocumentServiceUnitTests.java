package com.legalcase.service;

import com.legalcase.entity.Document;
import com.legalcase.entity.LegalCase;
import com.legalcase.entity.User;
import com.legalcase.enums.DocumentStatus;
import com.legalcase.enums.Role;
import com.legalcase.enums.TextExtractionStatus;
import com.legalcase.repository.CaseMemberRepository;
import com.legalcase.repository.CaseRepository;
import com.legalcase.repository.DocumentRepository;
import com.legalcase.repository.TaskRepository;
import com.legalcase.repository.UserRepository;
import com.legalcase.util.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import java.util.concurrent.CompletableFuture;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Document Service Unit Tests")
class DocumentServiceUnitTests {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private CaseRepository caseRepository;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CaseMemberRepository caseMemberRepository;

    @Mock
    private FileUtils fileUtils;

    @Mock
    private DocumentProcessingService processingService;

    @InjectMocks
    private DocumentService documentService;

    private User mockUser;
    private LegalCase mockLegalCase;
    private Document mockDocument;
    private MultipartFile mockFile;

    @BeforeEach
    void setUp() {
        mockUser = new User();
        mockUser.setId(1L);
        mockUser.setUsername("lawyerjohn");
        mockUser.setFullName("John Lawyer");
        mockUser.setEmail("john@legalfirm.com");
        mockUser.setRole(Role.LAWYER);

        mockLegalCase = new LegalCase();
        mockLegalCase.setId(1L);
        mockLegalCase.setCaseNumber("CASE-2026-00001");
        mockLegalCase.setTitle("Test Case");

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
        mockDocument.setTextExtractionStatus(TextExtractionStatus.PENDING);
        mockDocument.setStatus(DocumentStatus.ACTIVE);
        mockDocument.setVersion(1);
        mockDocument.setLatest(true);

        mockFile = new MockMultipartFile(
                "file",
                "contract.pdf",
                "application/pdf",
                "Test PDF content".getBytes()
        );
    }

    @Test
    @DisplayName("Should upload document to case successfully")
    void uploadToCase_Success() {
        when(caseRepository.findById(1L)).thenReturn(Optional.of(mockLegalCase));
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(caseMemberRepository.existsByLegalCaseAndUser(mockLegalCase, mockUser)).thenReturn(true);

        doNothing().when(fileUtils).validateFile(any());
        when(fileUtils.getFileExtension(anyString())).thenReturn("pdf");
        when(fileUtils.generateStorageFileName(anyString())).thenReturn("abc123_contract.pdf");
        when(fileUtils.generateStoragePath(anyLong(), isNull(), anyString())).thenReturn("cases/1/2026/05/abc123_contract.pdf");
        when(fileUtils.uploadToS3(any(), anyString()))
                .thenReturn("cases/1/2026/05/abc123_contract.pdf");
        when(documentRepository.save(any(Document.class))).thenReturn(mockDocument);
        when(processingService.extractTextAsync(any(Document.class)))
                .thenReturn(CompletableFuture.completedFuture(null));
        Document result = documentService.uploadToCase(mockFile, 1L, 1L, "Test contract", "contract,legal");

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getOriginalFileName()).isEqualTo("contract.pdf");
        assertThat(result.getCaseId()).isEqualTo(1L);

        verify(documentRepository, times(1)).save(any(Document.class));
        verify(processingService, times(1)).extractTextAsync(any(Document.class));
    }

    @Test
    @DisplayName("Should throw exception when user is not a case member")
    void uploadToCase_UserNotMember_ThrowsException() {
        when(caseRepository.findById(1L)).thenReturn(Optional.of(mockLegalCase));
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(caseMemberRepository.existsByLegalCaseAndUser(mockLegalCase, mockUser)).thenReturn(false);

        assertThatThrownBy(() -> documentService.uploadToCase(mockFile, 1L, 1L, null, null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Only case members can upload documents");
    }

    @Test
    @DisplayName("Should find document by ID")
    void findById_Success() {
        when(documentRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(mockDocument));

        Document result = documentService.findById(1L);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        verify(documentRepository, times(1)).findByIdAndIsDeletedFalse(1L);
    }

    @Test
    @DisplayName("Should throw exception when document not found")
    void findById_NotFound_ThrowsException() {
        when(documentRepository.findByIdAndIsDeletedFalse(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> documentService.findById(999L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Document not found");
    }

    @Test
    @DisplayName("Should get case documents")
    void getCaseDocuments_Success() {
        List<Document> documents = Arrays.asList(mockDocument);
        when(documentRepository.findByCaseIdAndIsDeletedFalseOrderByUploadedAtDesc(1L)).thenReturn(documents);

        List<Document> result = documentService.getCaseDocuments(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(1L);
        verify(documentRepository, times(1)).findByCaseIdAndIsDeletedFalseOrderByUploadedAtDesc(1L);
    }

    @Test
    @DisplayName("Should soft delete document")
    void softDelete_Success() {
        when(documentRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(mockDocument));
        doNothing().when(documentRepository).softDelete(eq(1L), any(LocalDateTime.class));

        documentService.softDelete(1L);

        verify(documentRepository, times(1)).softDelete(eq(1L), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("Should restore soft-deleted document")
    void restore_Success() {

        doNothing().when(documentRepository).restore(1L);

        documentService.restore(1L);

        verify(documentRepository, times(1)).restore(1L);
    }

    @Test
    @DisplayName("Should update document metadata")
    void updateMetadata_Success() {
        when(documentRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(mockDocument));
        when(documentRepository.save(any(Document.class))).thenReturn(mockDocument);

        Document result = documentService.updateMetadata(1L, "Updated description", "updated,tags");

        assertThat(result).isNotNull();
        verify(documentRepository, times(1)).save(any(Document.class));
    }

    @Test
    @DisplayName("Should get extracted text")
    void getExtractedText_Success() {
        mockDocument.setExtractedText("This is extracted text");
        when(documentRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(mockDocument));

        String result = documentService.getExtractedText(1L);

        assertThat(result).isEqualTo("This is extracted text");
    }

    @Test
    @DisplayName("Should throw exception when text extraction not completed")
    void getExtractedText_NotCompleted_ThrowsException() {
        mockDocument.setExtractedText(null);
        when(documentRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(mockDocument));

        assertThatThrownBy(() -> documentService.getExtractedText(1L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Text extraction not yet completed");
    }
}

