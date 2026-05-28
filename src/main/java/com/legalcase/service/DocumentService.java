package com.legalcase.service;

import com.legalcase.entity.Document;
import com.legalcase.entity.LegalCase;
import com.legalcase.entity.Task;
import com.legalcase.entity.User;
import com.legalcase.enums.DocumentStatus;
import com.legalcase.enums.Role;
import com.legalcase.enums.TextExtractionStatus;
import com.legalcase.exception.*;
import com.legalcase.repository.*;
import com.legalcase.util.FileUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.Year;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final CaseRepository caseRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final CaseMemberRepository caseMemberRepository;
    private final FileUtils fileUtils;
    private final DocumentProcessingService processingService;

    // ============================================
    // HELPER METHODS
    // ============================================

    private User findUserByIdentifier(String identifier) {
        return userRepository.findByUsername(identifier)
                .or(() -> userRepository.findByEmail(identifier))
                .orElseThrow(() -> new ResourceNotFoundException("User", "username or email", identifier));
    }

    private String generateDocumentNumber() {
        String year = String.valueOf(Year.now());
        long count = documentRepository.count() + 1;
        return "DOC-" + year + "-" + String.format("%06d", count);
    }

    private void verifyCaseMembership(LegalCase legalCase, User user) {
        if (!caseMemberRepository.existsByLegalCaseAndUser(legalCase, user)) {
            throw new AccessDeniedException("Only case members can access documents in this case");
        }
    }

    private void verifyDocumentAccess(Document document, User user) {
        boolean isAdmin = user.getRole() == Role.ADMIN;
        boolean isLawyer = user.getRole() == Role.LAWYER;
        boolean isUploader = document.getUploadedBy().getId().equals(user.getId());

        if (isAdmin) {
            return;
        }

        LegalCase legalCase = null;
        if (document.getLegalCase() != null) {
            legalCase = document.getLegalCase();
        } else if (document.getTask() != null) {
            legalCase = document.getTask().getLegalCase();
        }

        if (legalCase == null) {
            throw new BusinessException("Document has no associated case");
        }

        boolean isCaseMember = caseMemberRepository.existsByLegalCaseAndUser(legalCase, user);

        if (isLawyer && isCaseMember) {
            return;
        }

        if (isUploader) {
            return;
        }

        if (isCaseMember) {
            return;
        }

        throw new AccessDeniedException("You don't have permission to access this document");
    }

    private void verifyDocumentDeletePermission(Document document, User user) {
        boolean isAdmin = user.getRole() == Role.ADMIN;
        boolean isLawyer = user.getRole() == Role.LAWYER;
        boolean isUploader = document.getUploadedBy().getId().equals(user.getId());

        if (isAdmin) {
            return;
        }

        LegalCase legalCase = null;
        if (document.getLegalCase() != null) {
            legalCase = document.getLegalCase();
        } else if (document.getTask() != null) {
            legalCase = document.getTask().getLegalCase();
        }

        if (legalCase != null && isLawyer && caseMemberRepository.existsByLegalCaseAndUser(legalCase, user)) {
            return;
        }

        if (isUploader) {
            return;
        }

        throw new AccessDeniedException("You don't have permission to delete this document");
    }

    private void verifyDocumentUpdatePermission(Document document, User user) {
        boolean isAdmin = user.getRole() == Role.ADMIN;
        boolean isLawyer = user.getRole() == Role.LAWYER;
        boolean isUploader = document.getUploadedBy().getId().equals(user.getId());

        if (isAdmin || isLawyer || isUploader) {
            return;
        }

        throw new AccessDeniedException("You don't have permission to update this document");
    }

    private LegalCase findCaseByIdentifier(String identifier) {
        try {
            Long id = Long.parseLong(identifier);
            return caseRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Case", id));
        } catch (NumberFormatException e) {
            return caseRepository.findByCaseNumberWithDetails(identifier)
                    .orElseThrow(() -> new ResourceNotFoundException("Case", "caseNumber", identifier));
        }
    }

    private Task findTaskByIdentifier(String identifier) {
        try {
            Long id = Long.parseLong(identifier);
            return taskRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Task", id));
        } catch (NumberFormatException e) {
            return taskRepository.findByTaskNumber(identifier)
                    .orElseThrow(() -> new ResourceNotFoundException("Task", "taskNumber", identifier));
        }
    }

    // ============================================
    // UPLOAD DOCUMENTS
    // ============================================

    @Transactional
    public Document uploadToCase(MultipartFile file, String caseIdentifier, String userIdentifier,
                                 String description, String tags) {
        log.info("Uploading document to case: {} by user: {}", caseIdentifier, userIdentifier);

        LegalCase legalCase = findCaseByIdentifier(caseIdentifier);
        User uploader = findUserByIdentifier(userIdentifier);

        verifyCaseMembership(legalCase, uploader);
        validateAndUploadFile(file);

        String storedFileName = fileUtils.generateStorageFileName(file.getOriginalFilename());
        String storagePath = fileUtils.generateStoragePath(legalCase, null, storedFileName);
        fileUtils.uploadToS3(file, storagePath);

        Document document = createDocumentEntity(file, storagePath, uploader, description, tags);
        document.setLegalCase(legalCase);
        document.setDocumentNumber(generateDocumentNumber());

        Document saved = documentRepository.save(document);
        log.info("Document uploaded with ID: {}, Document Number: {}", saved.getId(), saved.getDocumentNumber());

        processingService.extractTextAsync(saved);

        return saved;
    }

    @Transactional
    public Document uploadToTask(MultipartFile file, String taskIdentifier, String userIdentifier,
                                 String description, String tags) {
        log.info("Uploading document to task: {} by user: {}", taskIdentifier, userIdentifier);

        Task task = findTaskByIdentifier(taskIdentifier);
        User uploader = findUserByIdentifier(userIdentifier);

        verifyCaseMembership(task.getLegalCase(), uploader);
        validateAndUploadFile(file);

        String storedFileName = fileUtils.generateStorageFileName(file.getOriginalFilename());
        String storagePath = fileUtils.generateStoragePath(null, task, storedFileName);
        fileUtils.uploadToS3(file, storagePath);

        Document document = createDocumentEntity(file, storagePath, uploader, description, tags);
        document.setTask(task);
        document.setLegalCase(task.getLegalCase());
        document.setDocumentNumber(generateDocumentNumber());

        Document saved = documentRepository.save(document);
        log.info("Document uploaded with ID: {}, Document Number: {}", saved.getId(), saved.getDocumentNumber());

        processingService.extractTextAsync(saved);

        return saved;
    }

    private void validateAndUploadFile(MultipartFile file) {
        try {
            fileUtils.validateFile(file);
        } catch (Exception e) {
            throw new FileProcessingException("File validation failed: " + e.getMessage());
        }
    }

    private Document createDocumentEntity(MultipartFile file, String storagePath, User uploader,
                                          String description, String tags) {
        String originalFileName = file.getOriginalFilename();
        String extension = fileUtils.getFileExtension(originalFileName);

        Document document = new Document();
        document.setFileName(storagePath.substring(storagePath.lastIndexOf('/') + 1));
        document.setOriginalFileName(originalFileName);
        document.setFileType(file.getContentType());
        document.setFileExtension(extension);
        document.setFileSize(file.getSize());
        document.setMimeType(file.getContentType());
        document.setStoragePath(storagePath);
        document.setUploadedBy(uploader);
        document.setDescription(description);
        document.setTags(tags);
        document.setStatus(DocumentStatus.ACTIVE);
        document.setTextExtractionStatus(TextExtractionStatus.PENDING);
        document.setVersion(1);
        document.setLatest(true);

        return document;
    }

    // ============================================
    // FIND DOCUMENTS
    // ============================================

    public Document findByIdentifier(String identifier) {
        try {
            Long id = Long.parseLong(identifier);
            return documentRepository.findByIdAndIsDeletedFalse(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Document", id));
        } catch (NumberFormatException e) {
            return documentRepository.findByDocumentNumberAndIsDeletedFalse(identifier)
                    .orElseThrow(() -> new ResourceNotFoundException("Document", "documentNumber", identifier));
        }
    }

    public Document findById(Long id) {
        return documentRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document", id));
    }

    // ============================================
    // GET DOCUMENTS
    // ============================================

    public List<Document> getCaseDocuments(String caseIdentifier, String userIdentifier) {
        LegalCase legalCase = findCaseByIdentifier(caseIdentifier);
        User user = findUserByIdentifier(userIdentifier);
        verifyCaseMembership(legalCase, user);
        return documentRepository.findByLegalCaseAndIsDeletedFalseOrderByUploadedAtDesc(legalCase);
    }

    public Page<Document> getCaseDocumentsPaginated(String caseIdentifier, String userIdentifier, Pageable pageable) {
        LegalCase legalCase = findCaseByIdentifier(caseIdentifier);
        User user = findUserByIdentifier(userIdentifier);
        verifyCaseMembership(legalCase, user);
        return documentRepository.findByLegalCaseAndIsDeletedFalse(legalCase, pageable);
    }

    public List<Document> getTaskDocuments(String taskIdentifier, String userIdentifier) {
        Task task = findTaskByIdentifier(taskIdentifier);
        User user = findUserByIdentifier(userIdentifier);
        verifyCaseMembership(task.getLegalCase(), user);
        return documentRepository.findByTaskAndIsDeletedFalseOrderByUploadedAtDesc(task);
    }

    public Page<Document> getTaskDocumentsPaginated(String taskIdentifier, String userIdentifier, Pageable pageable) {
        Task task = findTaskByIdentifier(taskIdentifier);
        User user = findUserByIdentifier(userIdentifier);
        verifyCaseMembership(task.getLegalCase(), user);
        return documentRepository.findByTaskAndIsDeletedFalse(task, pageable);
    }

    public List<Document> getMyDocuments(String userIdentifier) {
        User user = findUserByIdentifier(userIdentifier);
        return documentRepository.findByUploadedByAndIsDeletedFalseOrderByUploadedAtDesc(user);
    }

    public Page<Document> getMyDocumentsPaginated(String userIdentifier, Pageable pageable) {
        User user = findUserByIdentifier(userIdentifier);
        return documentRepository.findByUploadedByAndIsDeletedFalse(user, pageable);
    }

    // ============================================
    // SEARCH METHODS
    // ============================================

    public Page<Document> searchInCase(String caseIdentifier, String searchTerm, String userIdentifier, Pageable pageable) {
        LegalCase legalCase = findCaseByIdentifier(caseIdentifier);
        User user = findUserByIdentifier(userIdentifier);
        verifyCaseMembership(legalCase, user);
        return documentRepository.searchByLegalCase(legalCase, searchTerm, pageable);
    }

    public Page<Document> searchInTask(String taskIdentifier, String searchTerm, String userIdentifier, Pageable pageable) {
        Task task = findTaskByIdentifier(taskIdentifier);
        User user = findUserByIdentifier(userIdentifier);
        verifyCaseMembership(task.getLegalCase(), user);
        return documentRepository.searchByTask(task, searchTerm, pageable);
    }

    public Page<Document> searchMyDocuments(String searchTerm, String userIdentifier, Pageable pageable) {
        User user = findUserByIdentifier(userIdentifier);
        return documentRepository.searchByUploadedBy(user, searchTerm, pageable);
    }

    public Page<Document> adminGlobalSearch(String searchTerm, String userIdentifier, Pageable pageable) {
        User user = findUserByIdentifier(userIdentifier);
        if (user.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Only admins can perform global document search");
        }
        return documentRepository.adminGlobalSearch(searchTerm, pageable);
    }

    // ============================================
    // UPDATE METADATA (PATCH)
    // ============================================

    @Transactional
    public Document updateMetadata(String documentIdentifier, String description, String tags,
                                   String userIdentifier, String reason) {
        log.info("User {} updating document metadata: {}", userIdentifier, documentIdentifier);

        Document document = findByIdentifier(documentIdentifier);
        User user = findUserByIdentifier(userIdentifier);

        verifyDocumentUpdatePermission(document, user);

        LocalDateTime now = LocalDateTime.now();

        String editRecord = String.format(
                "{\"timestamp\":\"%s\",\"editedBy\":%d,\"editedByName\":\"%s\",\"oldDescription\":\"%s\",\"oldTags\":\"%s\",\"newDescription\":\"%s\",\"newTags\":\"%s\",\"reason\":\"%s\"}|",
                now.toString(), user.getId(), user.getFullName(),
                escapeJson(document.getDescription()), escapeJson(document.getTags()),
                escapeJson(description), escapeJson(tags),
                reason != null ? escapeJson(reason) : ""
        );

        documentRepository.updateMetadata(
                document.getId(), description, tags,
                user.getId(), user.getFullName(), now, editRecord
        );

        document = findByIdentifier(documentIdentifier);
        log.info("Document {} metadata updated", documentIdentifier);

        return document;
    }

    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    // ============================================
    // DELETE & RESTORE
    // ============================================

    @Transactional
    public void softDelete(String documentIdentifier, String userIdentifier, String reason) {
        log.info("User {} deleting document: {}", userIdentifier, documentIdentifier);

        Document document = findByIdentifier(documentIdentifier);
        User user = findUserByIdentifier(userIdentifier);

        verifyDocumentDeletePermission(document, user);

        documentRepository.softDelete(document.getId(), LocalDateTime.now(), user.getId());
        log.info("Document {} soft deleted", documentIdentifier);
    }

    @Transactional
    public void restore(String documentIdentifier, String userIdentifier) {
        log.info("User {} restoring document: {}", userIdentifier, documentIdentifier);

        User user = findUserByIdentifier(userIdentifier);

        if (user.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Only admins can restore documents");
        }

        Document document = findByIdentifier(documentIdentifier);
        documentRepository.restore(document.getId());
        log.info("Document {} restored", documentIdentifier);
    }

    @Transactional
    public void permanentlyDelete(String documentIdentifier, String userIdentifier) {
        log.info("User {} permanently deleting document: {}", userIdentifier, documentIdentifier);

        User user = findUserByIdentifier(userIdentifier);

        if (user.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Only admins can permanently delete documents");
        }

        Document document = findByIdentifier(documentIdentifier);

        try {
            fileUtils.deleteFromS3(document.getStoragePath());
        } catch (Exception e) {
            throw new FileProcessingException("Failed to delete file from S3: " + e.getMessage());
        }

        documentRepository.deleteById(document.getId());
        log.info("Document {} permanently deleted", documentIdentifier);
    }

    // ============================================
    // DOWNLOAD & TEXT
    // ============================================

    public String getDownloadUrl(String documentIdentifier, String userIdentifier) {
        Document document = findByIdentifier(documentIdentifier);
        User user = findUserByIdentifier(userIdentifier);
        verifyDocumentAccess(document, user);

        try {
            return fileUtils.generatePresignedUrl(document.getStoragePath());
        } catch (Exception e) {
            throw new FileProcessingException("Failed to generate download URL: " + e.getMessage());
        }
    }

    public String getExtractedText(String documentIdentifier, String userIdentifier) {
        Document document = findByIdentifier(documentIdentifier);
        User user = findUserByIdentifier(userIdentifier);
        verifyDocumentAccess(document, user);

        if (document.getExtractedText() == null) {
            throw new BusinessException("Text extraction not yet completed for document: " + documentIdentifier);
        }
        return document.getExtractedText();
    }
}