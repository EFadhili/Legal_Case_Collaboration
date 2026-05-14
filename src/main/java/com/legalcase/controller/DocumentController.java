package com.legalcase.controller;

import com.legalcase.dto.request.UpdateDocumentMetadataRequest;
import com.legalcase.dto.response.DocumentResponse;
import com.legalcase.entity.Document;
import com.legalcase.security.JwtUtils;
import com.legalcase.service.DocumentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/documents")
@RequiredArgsConstructor
@Slf4j
public class DocumentController {

    private final DocumentService documentService;
    private final JwtUtils jwtUtils;

    @PostMapping("/case/{caseId}")
    public ResponseEntity<DocumentResponse> uploadToCase(
            @PathVariable Long caseId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String tags,
            HttpServletRequest request) {

        Long userId = extractUserId(request);

        Document document = documentService.uploadToCase(file, caseId, userId, description, tags);
        String downloadUrl = documentService.getDownloadUrl(document);
        String uploadedByName = documentService.getUserFullName(userId);
        String uploadedByUsername = documentService.getUserUsername(userId);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(DocumentResponse.fromEntity(document, downloadUrl, uploadedByName, uploadedByUsername));
    }

    @PostMapping("/task/{taskId}")
    public ResponseEntity<DocumentResponse> uploadToTask(
            @PathVariable Long taskId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String tags,
            HttpServletRequest request) {

        Long userId = extractUserId(request);

        Document document = documentService.uploadToTask(file, taskId, userId, description, tags);
        String downloadUrl = documentService.getDownloadUrl(document);
        String uploadedByName = documentService.getUserFullName(userId);
        String uploadedByUsername = documentService.getUserUsername(userId);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(DocumentResponse.fromEntity(document, downloadUrl, uploadedByName, uploadedByUsername));
    }

    @GetMapping("/case/{caseId}")
    public ResponseEntity<List<DocumentResponse>> getCaseDocuments(@PathVariable Long caseId) {
        List<Document> documents = documentService.getCaseDocuments(caseId);
        List<DocumentResponse> responses = documents.stream()
                .map(doc -> DocumentResponse.fromEntity(doc,
                        documentService.getDownloadUrl(doc),
                        documentService.getUserFullName(doc.getUploadedById()),
                        documentService.getUserUsername(doc.getUploadedById())))
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    @GetMapping("/task/{taskId}")
    public ResponseEntity<List<DocumentResponse>> getTaskDocuments(@PathVariable Long taskId) {
        List<Document> documents = documentService.getTaskDocuments(taskId);
        List<DocumentResponse> responses = documents.stream()
                .map(doc -> DocumentResponse.fromEntity(doc,
                        documentService.getDownloadUrl(doc),
                        documentService.getUserFullName(doc.getUploadedById()),
                        documentService.getUserUsername(doc.getUploadedById())))
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    @GetMapping("/case/{caseId}/paginated")
    public ResponseEntity<Page<DocumentResponse>> getCaseDocumentsPaginated(
            @PathVariable Long caseId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<Document> documents = documentService.getCaseDocumentsPaginated(caseId, pageable);
        Page<DocumentResponse> responses = documents.map(doc ->
                DocumentResponse.fromEntity(doc,
                        documentService.getDownloadUrl(doc),
                        documentService.getUserFullName(doc.getUploadedById()),
                        documentService.getUserUsername(doc.getUploadedById())));

        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{id}")
    public ResponseEntity<DocumentResponse> getDocument(@PathVariable Long id) {
        Document document = documentService.findById(id);
        String downloadUrl = documentService.getDownloadUrl(document);
        String uploadedByName = documentService.getUserFullName(document.getUploadedById());
        String uploadedByUsername = documentService.getUserUsername(document.getUploadedById());

        return ResponseEntity.ok(DocumentResponse.fromEntity(document, downloadUrl, uploadedByName, uploadedByUsername));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Void> downloadDocument(@PathVariable Long id, HttpServletResponse response) {
        Document document = documentService.findById(id);
        String presignedUrl = documentService.getDownloadUrl(document);

        return ResponseEntity.status(HttpStatus.FOUND)
                .header("Location", presignedUrl)
                .build();
    }

    @GetMapping("/{id}/text")
    public ResponseEntity<String> getExtractedText(@PathVariable Long id) {
        String text = documentService.getExtractedText(id);
        return ResponseEntity.ok(text);
    }

    @PutMapping("/{id}")
    public ResponseEntity<DocumentResponse> updateMetadata(
            @PathVariable Long id,
            @Valid @RequestBody UpdateDocumentMetadataRequest request,
            HttpServletRequest httpRequest) {

        Document document = documentService.updateMetadata(id, request.getDescription(), request.getTags());
        String downloadUrl = documentService.getDownloadUrl(document);
        String uploadedByName = documentService.getUserFullName(document.getUploadedById());
        String uploadedByUsername = documentService.getUserUsername(document.getUploadedById());

        return ResponseEntity.ok(DocumentResponse.fromEntity(document, downloadUrl, uploadedByName, uploadedByUsername));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDocument(@PathVariable Long id) {
        documentService.softDelete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/restore")
    public ResponseEntity<DocumentResponse> restoreDocument(@PathVariable Long id) {
        documentService.restore(id);
        Document document = documentService.findById(id);
        String downloadUrl = documentService.getDownloadUrl(document);
        String uploadedByName = documentService.getUserFullName(document.getUploadedById());
        String uploadedByUsername = documentService.getUserUsername(document.getUploadedById());

        return ResponseEntity.ok(DocumentResponse.fromEntity(document, downloadUrl, uploadedByName, uploadedByUsername));
    }

    private Long extractUserId(HttpServletRequest request) {
        String token = extractToken(request);
        return jwtUtils.getUserIdFromToken(token);
    }

    private String extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new RuntimeException("Missing or invalid Authorization header");
        }
        return authHeader.substring(7);
    }
}