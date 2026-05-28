package com.legalcase.controller;

import com.legalcase.dto.request.UpdateDocumentMetadataRequest;
import com.legalcase.dto.response.DocumentResponse;
import com.legalcase.entity.Document;
import com.legalcase.security.JwtUtils;
import com.legalcase.service.DocumentProcessingService;
import com.legalcase.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Document Management", description = "Upload, download, and manage legal documents (PDF, DOCX, TXT, XLSX)")
@SecurityRequirement(name = "Bearer Authentication")
public class DocumentController {

    private final DocumentService documentService;
    private final DocumentProcessingService processingService;
    private final JwtUtils jwtUtils;

    // ============================================
    // UPLOAD ENDPOINTS
    // ============================================

    @Operation(summary = "Upload document to case",
            description = "Uploads a file to a specific case. Supports document number or case ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Document uploaded successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid file type or size exceeds 100MB"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Not a case member"),
            @ApiResponse(responseCode = "404", description = "Case not found")
    })
    @PostMapping("/case/{caseIdentifier}")
    public ResponseEntity<DocumentResponse> uploadToCase(
            @Parameter(description = "Case ID or Case Number (e.g., CASE-2026-00001)")
            @PathVariable String caseIdentifier,
            @Parameter(description = "File to upload")
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "Optional description")
            @RequestParam(required = false) String description,
            @Parameter(description = "Optional tags (comma-separated)")
            @RequestParam(required = false) String tags,
            HttpServletRequest request) {

        String userIdentifier = extractUserIdentifier(request);
        Document document = documentService.uploadToCase(file, caseIdentifier, userIdentifier, description, tags);
        String downloadUrl = documentService.getDownloadUrl(document.getDocumentNumber(), userIdentifier);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(DocumentResponse.fromEntity(document, downloadUrl));
    }

    @Operation(summary = "Upload document to task",
            description = "Uploads a file to a specific task. Supports task number or task ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Document uploaded successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid file type or size exceeds 100MB"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Not a case member"),
            @ApiResponse(responseCode = "404", description = "Task not found")
    })
    @PostMapping("/task/{taskIdentifier}")
    public ResponseEntity<DocumentResponse> uploadToTask(
            @Parameter(description = "Task ID or Task Number (e.g., TASK-2026-00123-001)")
            @PathVariable String taskIdentifier,
            @Parameter(description = "File to upload")
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "Optional description")
            @RequestParam(required = false) String description,
            @Parameter(description = "Optional tags (comma-separated)")
            @RequestParam(required = false) String tags,
            HttpServletRequest request) {

        String userIdentifier = extractUserIdentifier(request);
        Document document = documentService.uploadToTask(file, taskIdentifier, userIdentifier, description, tags);
        String downloadUrl = documentService.getDownloadUrl(document.getDocumentNumber(), userIdentifier);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(DocumentResponse.fromEntity(document, downloadUrl));
    }

    // ============================================
    // GET DOCUMENTS
    // ============================================

    @Operation(summary = "Get case documents",
            description = "Returns all documents associated with a case.")
    @GetMapping("/case/{caseIdentifier}")
    public ResponseEntity<List<DocumentResponse>> getCaseDocuments(
            @Parameter(description = "Case ID or Case Number")
            @PathVariable String caseIdentifier,
            HttpServletRequest request) {

        String userIdentifier = extractUserIdentifier(request);
        List<Document> documents = documentService.getCaseDocuments(caseIdentifier, userIdentifier);
        List<DocumentResponse> responses = documents.stream()
                .map(doc -> DocumentResponse.fromEntity(doc,
                        documentService.getDownloadUrl(doc.getDocumentNumber(), userIdentifier)))
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    @Operation(summary = "Get case documents (paginated)",
            description = "Returns documents for a case with pagination support.")
    @GetMapping("/case/{caseIdentifier}/paginated")
    public ResponseEntity<Page<DocumentResponse>> getCaseDocumentsPaginated(
            @Parameter(description = "Case ID or Case Number")
            @PathVariable String caseIdentifier,
            @Parameter(description = "Page number (0-indexed)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {

        String userIdentifier = extractUserIdentifier(request);
        Pageable pageable = PageRequest.of(page, size);
        Page<Document> documents = documentService.getCaseDocumentsPaginated(caseIdentifier, userIdentifier, pageable);

        return ResponseEntity.ok(documents.map(doc ->
                DocumentResponse.fromEntity(doc, documentService.getDownloadUrl(doc.getDocumentNumber(), userIdentifier))));
    }

    @Operation(summary = "Get task documents",
            description = "Returns all documents associated with a task.")
    @GetMapping("/task/{taskIdentifier}")
    public ResponseEntity<List<DocumentResponse>> getTaskDocuments(
            @Parameter(description = "Task ID or Task Number")
            @PathVariable String taskIdentifier,
            HttpServletRequest request) {

        String userIdentifier = extractUserIdentifier(request);
        List<Document> documents = documentService.getTaskDocuments(taskIdentifier, userIdentifier);
        List<DocumentResponse> responses = documents.stream()
                .map(doc -> DocumentResponse.fromEntity(doc,
                        documentService.getDownloadUrl(doc.getDocumentNumber(), userIdentifier)))
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    @Operation(summary = "Get task documents (paginated)",
            description = "Returns documents for a task with pagination support.")
    @GetMapping("/task/{taskIdentifier}/paginated")
    public ResponseEntity<Page<DocumentResponse>> getTaskDocumentsPaginated(
            @Parameter(description = "Task ID or Task Number")
            @PathVariable String taskIdentifier,
            @Parameter(description = "Page number (0-indexed)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {

        String userIdentifier = extractUserIdentifier(request);
        Pageable pageable = PageRequest.of(page, size);
        Page<Document> documents = documentService.getTaskDocumentsPaginated(taskIdentifier, userIdentifier, pageable);

        return ResponseEntity.ok(documents.map(doc ->
                DocumentResponse.fromEntity(doc, documentService.getDownloadUrl(doc.getDocumentNumber(), userIdentifier))));
    }

    @Operation(summary = "Get my documents",
            description = "Returns all documents uploaded by the current user.")
    @GetMapping("/my")
    public ResponseEntity<List<DocumentResponse>> getMyDocuments(HttpServletRequest request) {
        String userIdentifier = extractUserIdentifier(request);
        List<Document> documents = documentService.getMyDocuments(userIdentifier);
        List<DocumentResponse> responses = documents.stream()
                .map(doc -> DocumentResponse.fromEntity(doc,
                        documentService.getDownloadUrl(doc.getDocumentNumber(), userIdentifier)))
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    @Operation(summary = "Get my documents (paginated)",
            description = "Returns documents uploaded by the current user with pagination.")
    @GetMapping("/my/paginated")
    public ResponseEntity<Page<DocumentResponse>> getMyDocumentsPaginated(
            @Parameter(description = "Page number (0-indexed)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {

        String userIdentifier = extractUserIdentifier(request);
        Pageable pageable = PageRequest.of(page, size);
        Page<Document> documents = documentService.getMyDocumentsPaginated(userIdentifier, pageable);

        return ResponseEntity.ok(documents.map(doc ->
                DocumentResponse.fromEntity(doc, documentService.getDownloadUrl(doc.getDocumentNumber(), userIdentifier))));
    }

    // ============================================
    // GET SINGLE DOCUMENT
    // ============================================

    @Operation(summary = "Get document by ID or document number",
            description = "Retrieves document metadata.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Document found"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Access denied"),
            @ApiResponse(responseCode = "404", description = "Document not found")
    })
    @GetMapping("/{documentIdentifier}")
    public ResponseEntity<DocumentResponse> getDocument(
            @Parameter(description = "Document ID or Document Number (e.g., DOC-2026-00001)")
            @PathVariable String documentIdentifier,
            HttpServletRequest request) {

        String userIdentifier = extractUserIdentifier(request);
        Document document = documentService.findByIdentifier(documentIdentifier);
        String downloadUrl = documentService.getDownloadUrl(document.getDocumentNumber(), userIdentifier);

        return ResponseEntity.ok(DocumentResponse.fromEntity(document, downloadUrl));
    }

    // ============================================
    // UPDATE METADATA (PATCH)
    // ============================================

    @Operation(summary = "Update document metadata",
            description = "Updates description and tags for a document. Uses PATCH for partial update.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Metadata updated successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Not authorized to update"),
            @ApiResponse(responseCode = "404", description = "Document not found")
    })
    @PatchMapping("/{documentIdentifier}")
    public ResponseEntity<DocumentResponse> updateMetadata(
            @Parameter(description = "Document ID or Document Number")
            @PathVariable String documentIdentifier,
            @Valid @RequestBody UpdateDocumentMetadataRequest request,
            HttpServletRequest httpRequest) {

        String userIdentifier = extractUserIdentifier(httpRequest);
        Document document = documentService.updateMetadata(documentIdentifier, request.getDescription(),
                request.getTags(), userIdentifier, request.getReason());
        String downloadUrl = documentService.getDownloadUrl(document.getDocumentNumber(), userIdentifier);

        return ResponseEntity.ok(DocumentResponse.fromEntity(document, downloadUrl));
    }

    // ============================================
    // SEARCH ENDPOINTS
    // ============================================

    @Operation(summary = "Search documents in case",
            description = "Search documents within a specific case by document number, filename, description, or tags.")
    @GetMapping("/case/{caseIdentifier}/search")
    public ResponseEntity<Page<DocumentResponse>> searchInCase(
            @Parameter(description = "Case ID or Case Number")
            @PathVariable String caseIdentifier,
            @Parameter(description = "Search term (partial match)")
            @RequestParam String q,
            @Parameter(description = "Page number (0-indexed)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {

        String userIdentifier = extractUserIdentifier(request);
        Pageable pageable = PageRequest.of(page, size);
        Page<Document> documents = documentService.searchInCase(caseIdentifier, q, userIdentifier, pageable);

        return ResponseEntity.ok(documents.map(doc ->
                DocumentResponse.fromEntity(doc, documentService.getDownloadUrl(doc.getDocumentNumber(), userIdentifier))));
    }

    @Operation(summary = "Search documents in task",
            description = "Search documents within a specific task by document number, filename, description, or tags.")
    @GetMapping("/task/{taskIdentifier}/search")
    public ResponseEntity<Page<DocumentResponse>> searchInTask(
            @Parameter(description = "Task ID or Task Number")
            @PathVariable String taskIdentifier,
            @Parameter(description = "Search term (partial match)")
            @RequestParam String q,
            @Parameter(description = "Page number (0-indexed)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {

        String userIdentifier = extractUserIdentifier(request);
        Pageable pageable = PageRequest.of(page, size);
        Page<Document> documents = documentService.searchInTask(taskIdentifier, q, userIdentifier, pageable);

        return ResponseEntity.ok(documents.map(doc ->
                DocumentResponse.fromEntity(doc, documentService.getDownloadUrl(doc.getDocumentNumber(), userIdentifier))));
    }

    @Operation(summary = "Search my documents",
            description = "Search documents uploaded by the current user.")
    @GetMapping("/my/search")
    public ResponseEntity<Page<DocumentResponse>> searchMyDocuments(
            @Parameter(description = "Search term (partial match)")
            @RequestParam String q,
            @Parameter(description = "Page number (0-indexed)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {

        String userIdentifier = extractUserIdentifier(request);
        Pageable pageable = PageRequest.of(page, size);
        Page<Document> documents = documentService.searchMyDocuments(q, userIdentifier, pageable);

        return ResponseEntity.ok(documents.map(doc ->
                DocumentResponse.fromEntity(doc, documentService.getDownloadUrl(doc.getDocumentNumber(), userIdentifier))));
    }

    @Operation(summary = "Admin global search",
            description = "Search all documents in the system. Admin only.")
    @GetMapping("/admin/search")
    public ResponseEntity<Page<DocumentResponse>> adminGlobalSearch(
            @Parameter(description = "Search term (partial match)")
            @RequestParam String q,
            @Parameter(description = "Page number (0-indexed)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {

        String userIdentifier = extractUserIdentifier(request);
        Pageable pageable = PageRequest.of(page, size);
        Page<Document> documents = documentService.adminGlobalSearch(q, userIdentifier, pageable);

        return ResponseEntity.ok(documents.map(doc ->
                DocumentResponse.fromEntity(doc, documentService.getDownloadUrl(doc.getDocumentNumber(), userIdentifier))));
    }

    // ============================================
    // DOWNLOAD & TEXT EXTRACTION
    // ============================================

    @Operation(summary = "Download document",
            description = "Redirects to a presigned S3 URL for temporary file access (valid for 24 hours).")
    @GetMapping("/{documentIdentifier}/download")
    public ResponseEntity<Void> downloadDocument(
            @Parameter(description = "Document ID or Document Number")
            @PathVariable String documentIdentifier,
            HttpServletRequest request,
            HttpServletResponse response) {

        String userIdentifier = extractUserIdentifier(request);
        String presignedUrl = documentService.getDownloadUrl(documentIdentifier, userIdentifier);

        return ResponseEntity.status(HttpStatus.FOUND)
                .header("Location", presignedUrl)
                .build();
    }

    @Operation(summary = "Get extracted text",
            description = "Returns the text extracted from the document for AI analysis.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Text retrieved"),
            @ApiResponse(responseCode = "400", description = "Text extraction not yet completed"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @GetMapping("/{documentIdentifier}/text")
    public ResponseEntity<String> getExtractedText(
            @Parameter(description = "Document ID or Document Number")
            @PathVariable String documentIdentifier,
            HttpServletRequest request) {

        String userIdentifier = extractUserIdentifier(request);
        String text = documentService.getExtractedText(documentIdentifier, userIdentifier);
        return ResponseEntity.ok(text);
    }

    @Operation(summary = "Manually trigger text extraction",
            description = "Triggers async text extraction for a document. Useful if automatic extraction failed.")
    @PostMapping("/{documentIdentifier}/process")
    public ResponseEntity<Void> processDocument(
            @Parameter(description = "Document ID or Document Number")
            @PathVariable String documentIdentifier,
            HttpServletRequest request) {

        String userIdentifier = extractUserIdentifier(request);
        log.info("User {} manually triggering text extraction for document: {}", userIdentifier, documentIdentifier);

        Document document = documentService.findByIdentifier(documentIdentifier);
        processingService.extractTextAsync(document);

        return ResponseEntity.accepted().build();
    }

    // ============================================
    // DELETE & RESTORE
    // ============================================

    @Operation(summary = "Soft delete document",
            description = "Soft-deletes a document. Admin/Lawyer/Uploader can delete.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Document deleted successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Not authorized to delete"),
            @ApiResponse(responseCode = "404", description = "Document not found")
    })
    @DeleteMapping("/{documentIdentifier}")
    public ResponseEntity<Void> deleteDocument(
            @Parameter(description = "Document ID or Document Number")
            @PathVariable String documentIdentifier,
            @Parameter(description = "Optional reason for deletion")
            @RequestParam(required = false) String reason,
            HttpServletRequest request) {

        String userIdentifier = extractUserIdentifier(request);
        documentService.softDelete(documentIdentifier, userIdentifier, reason);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Restore document",
            description = "Restores a previously soft-deleted document. Admin only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Document restored successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Only admins can restore documents"),
            @ApiResponse(responseCode = "404", description = "Document not found")
    })
    @PostMapping("/{documentIdentifier}/restore")
    public ResponseEntity<DocumentResponse> restoreDocument(
            @Parameter(description = "Document ID or Document Number")
            @PathVariable String documentIdentifier,
            HttpServletRequest request) {

        String userIdentifier = extractUserIdentifier(request);
        documentService.restore(documentIdentifier, userIdentifier);
        Document document = documentService.findByIdentifier(documentIdentifier);
        String downloadUrl = documentService.getDownloadUrl(document.getDocumentNumber(), userIdentifier);

        return ResponseEntity.ok(DocumentResponse.fromEntity(document, downloadUrl));
    }

    // ============================================
    // HELPER METHODS
    // ============================================

    private String extractUserIdentifier(HttpServletRequest request) {
        String token = extractToken(request);
        return jwtUtils.getEmailFromToken(token);
    }

    private String extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new RuntimeException("Missing or invalid Authorization header");
        }
        return authHeader.substring(7);
    }
}