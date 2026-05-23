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

    @Operation(
            summary = "Upload document to case",
            description = "Uploads a file to a specific case. Supported formats: PDF, DOCX, TXT, XLSX. Max size: 100MB."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Document uploaded successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid file type or size exceeds 100MB"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Not a case member")
    })
    @PostMapping("/case/{caseId}")
    public ResponseEntity<DocumentResponse> uploadToCase(
            @Parameter(description = "Case ID") @PathVariable Long caseId,
            @Parameter(description = "File to upload") @RequestParam("file") MultipartFile file,
            @Parameter(description = "Optional description") @RequestParam(required = false) String description,
            @Parameter(description = "Optional tags (comma-separated)") @RequestParam(required = false) String tags,
            HttpServletRequest request) {

        Long userId = extractUserId(request);

        Document document = documentService.uploadToCase(file, caseId, userId, description, tags);
        String downloadUrl = documentService.getDownloadUrl(document);
        String uploadedByName = documentService.getUserFullName(userId);
        String uploadedByUsername = documentService.getUserUsername(userId);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(DocumentResponse.fromEntity(document, downloadUrl, uploadedByName, uploadedByUsername));
    }

    @Operation(
            summary = "Manually trigger text extraction",
            description = "Triggers async text extraction for a document. Useful if automatic extraction failed."
    )
    @PostMapping("/{id}/process")
    public ResponseEntity<Void> processDocument(
            @Parameter(description = "Document ID") @PathVariable Long id,
            HttpServletRequest httpRequest) {
        Long userId = extractUserId(httpRequest);
        log.info("User {} manually triggering text extraction for document: {}", userId, id);

        Document document = documentService.findById(id);
        processingService.extractTextAsync(document);

        return ResponseEntity.accepted().build();
    }

    @Operation(
            summary = "Upload document to task",
            description = "Uploads a file to a specific task. Task must belong to a case the user is a member of."
    )
    @PostMapping("/task/{taskId}")
    public ResponseEntity<DocumentResponse> uploadToTask(
            @Parameter(description = "Task ID") @PathVariable Long taskId,
            @Parameter(description = "File to upload") @RequestParam("file") MultipartFile file,
            @Parameter(description = "Optional description") @RequestParam(required = false) String description,
            @Parameter(description = "Optional tags (comma-separated)") @RequestParam(required = false) String tags,
            HttpServletRequest request) {

        Long userId = extractUserId(request);

        Document document = documentService.uploadToTask(file, taskId, userId, description, tags);
        String downloadUrl = documentService.getDownloadUrl(document);
        String uploadedByName = documentService.getUserFullName(userId);
        String uploadedByUsername = documentService.getUserUsername(userId);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(DocumentResponse.fromEntity(document, downloadUrl, uploadedByName, uploadedByUsername));
    }

    @Operation(
            summary = "Get case documents",
            description = "Returns all documents associated with a case."
    )
    @GetMapping("/case/{caseId}")
    public ResponseEntity<List<DocumentResponse>> getCaseDocuments(@Parameter(description = "Case ID") @PathVariable Long caseId) {
        List<Document> documents = documentService.getCaseDocuments(caseId);
        List<DocumentResponse> responses = documents.stream()
                .map(doc -> DocumentResponse.fromEntity(doc,
                        documentService.getDownloadUrl(doc),
                        documentService.getUserFullName(doc.getUploadedById()),
                        documentService.getUserUsername(doc.getUploadedById())))
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    @Operation(
            summary = "Get task documents",
            description = "Returns all documents associated with a task."
    )
    @GetMapping("/task/{taskId}")
    public ResponseEntity<List<DocumentResponse>> getTaskDocuments(@Parameter(description = "Task ID") @PathVariable Long taskId) {
        List<Document> documents = documentService.getTaskDocuments(taskId);
        List<DocumentResponse> responses = documents.stream()
                .map(doc -> DocumentResponse.fromEntity(doc,
                        documentService.getDownloadUrl(doc),
                        documentService.getUserFullName(doc.getUploadedById()),
                        documentService.getUserUsername(doc.getUploadedById())))
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    @Operation(
            summary = "Get case documents (paginated)",
            description = "Returns documents for a case with pagination support."
    )
    @GetMapping("/case/{caseId}/paginated")
    public ResponseEntity<Page<DocumentResponse>> getCaseDocumentsPaginated(
            @Parameter(description = "Case ID") @PathVariable Long caseId,
            @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<Document> documents = documentService.getCaseDocumentsPaginated(caseId, pageable);
        Page<DocumentResponse> responses = documents.map(doc ->
                DocumentResponse.fromEntity(doc,
                        documentService.getDownloadUrl(doc),
                        documentService.getUserFullName(doc.getUploadedById()),
                        documentService.getUserUsername(doc.getUploadedById())));

        return ResponseEntity.ok(responses);
    }

    @Operation(
            summary = "Get document by ID",
            description = "Retrieves document metadata (not the file content)."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Document found"),
            @ApiResponse(responseCode = "404", description = "Document not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<DocumentResponse> getDocument(@Parameter(description = "Document ID") @PathVariable Long id) {
        Document document = documentService.findById(id);
        String downloadUrl = documentService.getDownloadUrl(document);
        String uploadedByName = documentService.getUserFullName(document.getUploadedById());
        String uploadedByUsername = documentService.getUserUsername(document.getUploadedById());

        return ResponseEntity.ok(DocumentResponse.fromEntity(document, downloadUrl, uploadedByName, uploadedByUsername));
    }

    @Operation(
            summary = "Download document",
            description = "Redirects to a presigned S3 URL for temporary file access (valid for 24 hours)."
    )
    @GetMapping("/{id}/download")
    public ResponseEntity<Void> downloadDocument(
            @Parameter(description = "Document ID") @PathVariable Long id,
            HttpServletResponse response) {
        Document document = documentService.findById(id);
        String presignedUrl = documentService.getDownloadUrl(document);

        return ResponseEntity.status(HttpStatus.FOUND)
                .header("Location", presignedUrl)
                .build();
    }

    @Operation(
            summary = "Get extracted text",
            description = "Returns the text extracted from the document for AI analysis."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Text retrieved"),
            @ApiResponse(responseCode = "400", description = "Text extraction not yet completed")
    })
    @GetMapping("/{id}/text")
    public ResponseEntity<String> getExtractedText(@Parameter(description = "Document ID") @PathVariable Long id) {
        String text = documentService.getExtractedText(id);
        return ResponseEntity.ok(text);
    }

    @Operation(
            summary = "Update document metadata",
            description = "Updates description and tags for a document."
    )
    @PutMapping("/{id}")
    public ResponseEntity<DocumentResponse> updateMetadata(
            @Parameter(description = "Document ID") @PathVariable Long id,
            @Valid @RequestBody UpdateDocumentMetadataRequest request,
            HttpServletRequest httpRequest) {

        Document document = documentService.updateMetadata(id, request.getDescription(), request.getTags());
        String downloadUrl = documentService.getDownloadUrl(document);
        String uploadedByName = documentService.getUserFullName(document.getUploadedById());
        String uploadedByUsername = documentService.getUserUsername(document.getUploadedById());

        return ResponseEntity.ok(DocumentResponse.fromEntity(document, downloadUrl, uploadedByName, uploadedByUsername));
    }

    @Operation(
            summary = "Soft delete document",
            description = "Soft-deletes a document. Can be restored within 30 days."
    )
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDocument(@Parameter(description = "Document ID") @PathVariable Long id) {
        documentService.softDelete(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Restore document",
            description = "Restores a previously soft-deleted document."
    )
    @PostMapping("/{id}/restore")
    public ResponseEntity<DocumentResponse> restoreDocument(@Parameter(description = "Document ID") @PathVariable Long id) {
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