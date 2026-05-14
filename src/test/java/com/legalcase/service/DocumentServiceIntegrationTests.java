package com.legalcase.service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.PutObjectResult;
import com.legalcase.entity.Document;
import com.legalcase.entity.LegalCase;
import com.legalcase.entity.User;
import com.legalcase.enums.Role;
import com.legalcase.repository.CaseMemberRepository;
import com.legalcase.repository.CaseRepository;
import com.legalcase.repository.DocumentRepository;
import com.legalcase.repository.UserRepository;
import com.legalcase.util.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Document Service Integration Tests")
class DocumentServiceIntegrationTests {

    @Autowired
    private DocumentService documentService;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private CaseRepository caseRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CaseMemberRepository caseMemberRepository;

    @MockBean
    private AmazonS3 amazonS3;

    @MockBean
    private FileUtils fileUtils;

    private User lawyerUser;
    private LegalCase testCase;
    private MockMultipartFile mockFile;

    @BeforeEach
    void setUp() throws Exception {
        // Clear repositories
        documentRepository.deleteAll();
        caseMemberRepository.deleteAll();
        caseRepository.deleteAll();
        userRepository.deleteAll();

        // Create user
        lawyerUser = new User();
        lawyerUser.setUsername("lawyer");
        lawyerUser.setEmail("lawyer@test.com");
        lawyerUser.setPassword("$2a$10$encoded");
        lawyerUser.setFullName("Test Lawyer");
        lawyerUser.setRole(Role.LAWYER);
        lawyerUser.setActive(true);
        lawyerUser.setCreatedAt(LocalDateTime.now());
        lawyerUser.setUpdatedAt(LocalDateTime.now());
        lawyerUser = userRepository.save(lawyerUser);

        // Create case
        testCase = new LegalCase();
        testCase.setCaseNumber("CASE-2026-00001");
        testCase.setTitle("Integration Test Case");
        testCase.setDescription("Test case for document integration");
        testCase.setStatus(com.legalcase.enums.CaseStatus.OPEN);
        testCase.setPriority(com.legalcase.enums.CasePriority.MEDIUM);
        testCase.setType(com.legalcase.enums.CaseType.CIVIL);
        testCase.setOwner(lawyerUser);
        testCase.setFilingDate(LocalDateTime.now().toLocalDate());
        testCase.setCreatedAt(LocalDateTime.now());
        testCase.setUpdatedAt(LocalDateTime.now());
        testCase = caseRepository.save(testCase);

        // Create mock file
        mockFile = new MockMultipartFile(
                "file",
                "contract.pdf",
                "application/pdf",
                "Sample PDF content for testing".getBytes()
        );

        PutObjectResult putObjectResult = new PutObjectResult();

        // Mock Amazon S3 upload
        when(amazonS3.putObject(any(PutObjectRequest.class)))
                .thenReturn(new PutObjectResult());

// Mock file utils methods
        when(fileUtils.getFileExtension(anyString())).thenReturn("pdf");
        when(fileUtils.sanitizeFilename(anyString())).thenReturn("contract.pdf");
        when(fileUtils.generateStorageFileName(anyString())).thenReturn("mock_storage_file.pdf");
        when(fileUtils.generateStoragePath(anyLong(), isNull(), anyString()))
                .thenReturn("cases/1/2026/05/mock_storage_file.pdf");
        when(fileUtils.generatePresignedUrl(anyString()))
                .thenReturn("https://mock-s3-url.com/mock_storage_file.pdf");

// Mock void method
        doNothing().when(fileUtils).validateFile(any());

// Mock string-returning method
        when(fileUtils.extractTextFromFile(any(), anyString()))
                .thenReturn("Mock extracted text");
    }

    @Test
    @DisplayName("Should upload document to case successfully")
    void uploadToCase_Success() {
        Document result = documentService.uploadToCase(mockFile, testCase.getId(), lawyerUser.getId(), "Test contract", "contract,legal");

        assertThat(result).isNotNull();
        assertThat(result.getOriginalFileName()).isEqualTo("contract.pdf");
        assertThat(result.getCaseId()).isEqualTo(testCase.getId());
        assertThat(result.getUploadedById()).isEqualTo(lawyerUser.getId());
        assertThat(result.getDescription()).isEqualTo("Test contract");
        assertThat(result.getTags()).isEqualTo("contract,legal");
    }

    @Test
    @DisplayName("Should retrieve document by ID")
    void findById_Success() {
        Document saved = documentService.uploadToCase(mockFile, testCase.getId(), lawyerUser.getId(), null, null);

        Document found = documentService.findById(saved.getId());

        assertThat(found).isNotNull();
        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getOriginalFileName()).isEqualTo("contract.pdf");
    }

    @Test
    @DisplayName("Should get case documents")
    void getCaseDocuments_Success() {
        documentService.uploadToCase(mockFile, testCase.getId(), lawyerUser.getId(), "First doc", null);
        documentService.uploadToCase(mockFile, testCase.getId(), lawyerUser.getId(), "Second doc", null);

        var documents = documentService.getCaseDocuments(testCase.getId());

        assertThat(documents).hasSize(2);
    }

    @Test
    @DisplayName("Should update document metadata")
    void updateMetadata_Success() {
        Document saved = documentService.uploadToCase(mockFile, testCase.getId(), lawyerUser.getId(), "Original", "tag1");

        Document updated = documentService.updateMetadata(saved.getId(), "Updated description", "tag1,tag2");

        assertThat(updated.getDescription()).isEqualTo("Updated description");
        assertThat(updated.getTags()).isEqualTo("tag1,tag2");
    }

    @Test
    @DisplayName("Should soft delete document")
    void softDelete_Success() {
        Document saved = documentService.uploadToCase(mockFile, testCase.getId(), lawyerUser.getId(), null, null);

        documentService.softDelete(saved.getId());

        Document deleted = documentRepository.findById(saved.getId()).orElse(null);
        assertThat(deleted).isNotNull();
        assertThat(deleted.isDeleted()).isTrue();
        assertThat(deleted.getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should restore soft-deleted document")
    void restore_Success() {
        Document saved = documentService.uploadToCase(mockFile, testCase.getId(), lawyerUser.getId(), null, null);

        documentService.softDelete(saved.getId());
        documentService.restore(saved.getId());

        Document restored = documentRepository.findById(saved.getId()).orElse(null);
        assertThat(restored).isNotNull();
        assertThat(restored.isDeleted()).isFalse();
        assertThat(restored.getDeletedAt()).isNull();
    }
}