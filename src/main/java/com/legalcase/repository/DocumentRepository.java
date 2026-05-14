package com.legalcase.repository;

import com.legalcase.entity.Document;
import com.legalcase.enums.DocumentStatus;
import com.legalcase.enums.TextExtractionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {

    // Find by association
    List<Document> findByCaseIdAndIsDeletedFalseOrderByUploadedAtDesc(Long caseId);

    List<Document> findByTaskIdAndIsDeletedFalseOrderByUploadedAtDesc(Long taskId);

    Page<Document> findByCaseIdAndIsDeletedFalse(Long caseId, Pageable pageable);

    Page<Document> findByTaskIdAndIsDeletedFalse(Long taskId, Pageable pageable);

    // Find by uploader
    List<Document> findByUploadedByIdAndIsDeletedFalse(Long uploadedById);

    // Find active documents
    Optional<Document> findByIdAndIsDeletedFalse(Long id);

    // Find by version
    List<Document> findByParentDocumentIdOrderByVersionDesc(Long parentDocumentId);

    Optional<Document> findByCaseIdAndIsLatestTrueAndIsDeletedFalse(Long caseId);

    // Find documents pending extraction
    @Query("SELECT d FROM Document d WHERE d.textExtractionStatus = 'PENDING' AND d.isDeleted = false AND d.status = 'ACTIVE'")
    List<Document> findPendingExtractionDocuments();

    @Query("SELECT d FROM Document d WHERE d.textExtractionStatus = 'PROCESSING' AND d.processingStartedAt < :timeout")
    List<Document> findStuckProcessingDocuments(@Param("timeout") LocalDateTime timeout);

    // Update extraction status
    @Modifying
    @Query("UPDATE Document d SET d.textExtractionStatus = :status, d.processingProgress = :progress, " +
            "d.processingStartedAt = :startedAt WHERE d.id = :id")
    void updateExtractionStatus(@Param("id") Long id, @Param("status") TextExtractionStatus status,
                                @Param("progress") Integer progress, @Param("startedAt") LocalDateTime startedAt);

    @Modifying
    @Query("UPDATE Document d SET d.extractedText = :text, d.textExtractionStatus = 'COMPLETED', " +
            "d.processingProgress = 100, d.processedAt = :processedAt WHERE d.id = :id")
    void markExtractionComplete(@Param("id") Long id, @Param("text") String text,
                                @Param("processedAt") LocalDateTime processedAt);

    @Modifying
    @Query("UPDATE Document d SET d.textExtractionStatus = 'FAILED', d.textExtractionError = :error, " +
            "d.processingRetryCount = d.processingRetryCount + 1 WHERE d.id = :id")
    void markExtractionFailed(@Param("id") Long id, @Param("error") String error);

    // Soft delete
    @Modifying
    @Query("UPDATE Document d SET d.isDeleted = true, d.deletedAt = :deletedAt, d.status = 'DELETED' WHERE d.id = :id")
    void softDelete(@Param("id") Long id, @Param("deletedAt") LocalDateTime deletedAt);

    // Restore
    @Modifying
    @Query("UPDATE Document d SET d.isDeleted = false, d.deletedAt = null, d.status = 'ACTIVE' WHERE d.id = :id")
    void restore(@Param("id") Long id);

    // Find soft-deleted documents older than cutoff
    @Query("SELECT d FROM Document d WHERE d.isDeleted = true AND d.deletedAt < :cutoffDate")
    List<Document> findByIsDeletedTrueAndDeletedAtBefore(@Param("cutoffDate") LocalDateTime cutoffDate);

    // Delete all documents for a case
    @Modifying
    @Query("UPDATE Document d SET d.isDeleted = true, d.deletedAt = :deletedAt WHERE d.caseId = :caseId AND d.isDeleted = false")
    void softDeleteByCaseId(@Param("caseId") Long caseId, @Param("deletedAt") LocalDateTime deletedAt);
}