package com.legalcase.service;

import com.legalcase.entity.Document;
import com.legalcase.entity.User;
import com.legalcase.enums.DocumentStatus;
import com.legalcase.enums.TextExtractionStatus;
import com.legalcase.repository.CaseMemberRepository;
import com.legalcase.repository.CaseRepository;
import com.legalcase.repository.DocumentRepository;
import com.legalcase.repository.TaskRepository;
import com.legalcase.repository.UserRepository;
import com.legalcase.util.FileUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
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

    @Transactional
    public Document uploadToCase(MultipartFile file, Long caseId, Long uploadedById,
                                 String description, String tags) {
        log.info("Uploading document to case: {} by user: {}", caseId, uploadedById);

        var legalCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new RuntimeException("Case not found"));

        var uploader = userRepository.findById(uploadedById)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!caseMemberRepository.existsByLegalCaseAndUser(legalCase, uploader)) {
            throw new RuntimeException("Only case members can upload documents");
        }

        fileUtils.validateFile(file);
        String originalFileName = file.getOriginalFilename();
        String extension = fileUtils.getFileExtension(originalFileName);
        String storedFileName = fileUtils.generateStorageFileName(originalFileName);
        String storagePath = fileUtils.generateStoragePath(caseId, null, storedFileName);

        fileUtils.uploadToS3(file, storagePath);

        Document document = new Document();
        document.setFileName(storedFileName);
        document.setOriginalFileName(originalFileName);
        document.setFileType(file.getContentType());
        document.setFileExtension(extension);
        document.setFileSize(file.getSize());
        document.setMimeType(file.getContentType());
        document.setStoragePath(storagePath);
        document.setStorageBucket("legalcase-documents");
        document.setCaseId(caseId);
        document.setUploadedById(uploadedById);
        document.setDescription(description);
        document.setTags(tags);
        document.setStatus(DocumentStatus.ACTIVE);
        document.setTextExtractionStatus(TextExtractionStatus.PENDING);

        Document saved = documentRepository.save(document);
        log.info("Document uploaded to S3 with ID: {}", saved.getId());

        processingService.extractTextAsync(saved);

        return saved;
    }

    @Transactional
    public Document uploadToTask(MultipartFile file, Long taskId, Long uploadedById,
                                 String description, String tags) {
        log.info("Uploading document to task: {} by user: {}", taskId, uploadedById);

        var task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found"));

        var uploader = userRepository.findById(uploadedById)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!caseMemberRepository.existsByLegalCaseAndUser(task.getLegalCase(), uploader)) {
            throw new RuntimeException("Only case members can upload documents");
        }

        fileUtils.validateFile(file);
        String originalFileName = file.getOriginalFilename();
        String extension = fileUtils.getFileExtension(originalFileName);
        String storedFileName = fileUtils.generateStorageFileName(originalFileName);
        String storagePath = fileUtils.generateStoragePath(null, taskId, storedFileName);

        fileUtils.uploadToS3(file, storagePath);

        Document document = new Document();
        document.setFileName(storedFileName);
        document.setOriginalFileName(originalFileName);
        document.setFileType(file.getContentType());
        document.setFileExtension(extension);
        document.setFileSize(file.getSize());
        document.setMimeType(file.getContentType());
        document.setStoragePath(storagePath);
        document.setStorageBucket("legalcase-documents");
        document.setTaskId(taskId);
        document.setUploadedById(uploadedById);
        document.setDescription(description);
        document.setTags(tags);
        document.setStatus(DocumentStatus.ACTIVE);
        document.setTextExtractionStatus(TextExtractionStatus.PENDING);

        Document saved = documentRepository.save(document);
        log.info("Document uploaded to S3 with ID: {}", saved.getId());

        processingService.extractTextAsync(saved);

        return saved;
    }

    public Document findById(Long id) {
        return documentRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new RuntimeException("Document not found"));
    }

    public List<Document> getCaseDocuments(Long caseId) {
        return documentRepository.findByCaseIdAndIsDeletedFalseOrderByUploadedAtDesc(caseId);
    }

    public List<Document> getTaskDocuments(Long taskId) {
        return documentRepository.findByTaskIdAndIsDeletedFalseOrderByUploadedAtDesc(taskId);
    }

    public Page<Document> getCaseDocumentsPaginated(Long caseId, Pageable pageable) {
        return documentRepository.findByCaseIdAndIsDeletedFalse(caseId, pageable);
    }

    @Transactional
    public Document updateMetadata(Long documentId, String description, String tags) {
        Document document = findById(documentId);

        if (description != null) {
            document.setDescription(description);
        }
        if (tags != null) {
            document.setTags(tags);
        }

        return documentRepository.save(document);
    }

    @Transactional
    public void softDelete(Long documentId) {
        Document document = findById(documentId);
        documentRepository.softDelete(documentId, LocalDateTime.now());
        log.info("Document {} soft deleted", documentId);
    }

    @Transactional
    public void restore(Long documentId) {
        documentRepository.restore(documentId);
        log.info("Document {} restored", documentId);
    }

    @Transactional
    public void permanentlyDelete(Long documentId) {
        Document document = findById(documentId);
        fileUtils.deleteFromS3(document.getStoragePath());
        documentRepository.deleteById(documentId);
        log.info("Document {} permanently deleted from S3 and database", documentId);
    }

    public String getDownloadUrl(Document document) {
        return fileUtils.generatePresignedUrl(document.getStoragePath());
    }

    public String getExtractedText(Long documentId) {
        Document document = findById(documentId);
        if (document.getExtractedText() == null) {
            throw new RuntimeException("Text extraction not yet completed");
        }
        return document.getExtractedText();
    }

    public String getUserFullName(Long userId) {
        return userRepository.findById(userId)
                .map(User::getFullName)
                .orElse("Unknown");
    }

    public String getUserUsername(Long userId) {
        return userRepository.findById(userId)
                .map(User::getUsername)
                .orElse("unknown");
    }
}