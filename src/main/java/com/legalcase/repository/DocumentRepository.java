package com.legalcase.repository;

import com.legalcase.entity.Document;
import com.legalcase.entity.LegalCase;
import com.legalcase.entity.Task;
import com.legalcase.entity.User;
import com.legalcase.enums.DocumentStatus;
import com.legalcase.enums.TextExtractionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
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

    // ============================================
    // FIND BY ID WITH ENTITY GRAPH
    // ============================================

    @Override
    @EntityGraph(attributePaths = {"uploadedBy", "legalCase", "task", "task.legalCase", "parentDocument"})
    Optional<Document> findById(Long id);

    @EntityGraph(attributePaths = {"uploadedBy", "legalCase", "task", "task.legalCase", "parentDocument"})
    Optional<Document> findByIdAndIsDeletedFalse(Long id);

    @EntityGraph(attributePaths = {"uploadedBy", "legalCase", "task", "task.legalCase", "parentDocument"})
    Optional<Document> findByDocumentNumberAndIsDeletedFalse(String documentNumber);

    // ============================================
    // FIND BY ASSOCIATION (with EntityGraph)
    // ============================================

    @EntityGraph(attributePaths = {"uploadedBy", "legalCase", "task"})
    List<Document> findByLegalCaseAndIsDeletedFalseOrderByUploadedAtDesc(LegalCase legalCase);

    @EntityGraph(attributePaths = {"uploadedBy", "legalCase", "task"})
    Page<Document> findByLegalCaseAndIsDeletedFalse(LegalCase legalCase, Pageable pageable);

    @EntityGraph(attributePaths = {"uploadedBy", "legalCase", "task"})
    List<Document> findByTaskAndIsDeletedFalseOrderByUploadedAtDesc(Task task);

    @EntityGraph(attributePaths = {"uploadedBy", "legalCase", "task"})
    Page<Document> findByTaskAndIsDeletedFalse(Task task, Pageable pageable);

    @EntityGraph(attributePaths = {"uploadedBy", "legalCase", "task"})
    List<Document> findByUploadedByAndIsDeletedFalseOrderByUploadedAtDesc(User uploadedBy);

    @EntityGraph(attributePaths = {"uploadedBy", "legalCase", "task"})
    Page<Document> findByUploadedByAndIsDeletedFalse(User uploadedBy, Pageable pageable);

    // ============================================
    // SEARCH METHODS (with EntityGraph)
    // ============================================

    // Case-scoped search
    @EntityGraph(attributePaths = {"uploadedBy", "legalCase", "task"})
    @Query("SELECT d FROM Document d WHERE d.legalCase = :legalCase AND d.isDeleted = false AND " +
            "(LOWER(d.documentNumber) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(d.originalFileName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(d.description) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(d.tags) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) " +
            "ORDER BY d.uploadedAt DESC")
    Page<Document> searchByLegalCase(@Param("legalCase") LegalCase legalCase,
                                     @Param("searchTerm") String searchTerm,
                                     Pageable pageable);

    // Task-scoped search
    @EntityGraph(attributePaths = {"uploadedBy", "legalCase", "task"})
    @Query("SELECT d FROM Document d WHERE d.task = :task AND d.isDeleted = false AND " +
            "(LOWER(d.documentNumber) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(d.originalFileName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(d.description) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(d.tags) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) " +
            "ORDER BY d.uploadedAt DESC")
    Page<Document> searchByTask(@Param("task") Task task,
                                @Param("searchTerm") String searchTerm,
                                Pageable pageable);

    // User-scoped search (my documents)
    @EntityGraph(attributePaths = {"uploadedBy", "legalCase", "task"})
    @Query("SELECT d FROM Document d WHERE d.uploadedBy = :user AND d.isDeleted = false AND " +
            "(LOWER(d.documentNumber) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(d.originalFileName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(d.description) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(d.tags) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) " +
            "ORDER BY d.uploadedAt DESC")
    Page<Document> searchByUploadedBy(@Param("user") User user,
                                      @Param("searchTerm") String searchTerm,
                                      Pageable pageable);

    // Admin global search (all documents, with optional filters)
    @EntityGraph(attributePaths = {"uploadedBy", "legalCase", "task", "task.legalCase"})
    @Query("SELECT d FROM Document d WHERE d.isDeleted = false AND " +
            "(LOWER(d.documentNumber) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(d.originalFileName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(d.description) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(d.tags) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) " +
            "ORDER BY d.uploadedAt DESC")
    Page<Document> adminGlobalSearch(@Param("searchTerm") String searchTerm, Pageable pageable);

    // ============================================
    // PROCESSING QUERIES
    // ============================================

    @Query("SELECT d FROM Document d WHERE d.textExtractionStatus = 'PENDING' AND d.isDeleted = false AND d.status = 'ACTIVE'")
    List<Document> findPendingExtractionDocuments();

    @Query("SELECT d FROM Document d WHERE d.textExtractionStatus = 'PROCESSING' AND d.processingStartedAt < :timeout")
    List<Document> findStuckProcessingDocuments(@Param("timeout") LocalDateTime timeout);

    // ============================================
    // VERSIONING
    // ============================================

    @EntityGraph(attributePaths = {"uploadedBy"})
    List<Document> findByParentDocumentOrderByVersionDesc(Document parentDocument);

    Optional<Document> findByLegalCaseAndIsLatestTrueAndIsDeletedFalse(LegalCase legalCase);

    // ============================================
    // UPDATE METHODS
    // ============================================

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

    // ============================================
    // SOFT DELETE
    // ============================================

    @Modifying
    @Query("UPDATE Document d SET d.isDeleted = true, d.deletedAt = :deletedAt, d.deletedBy = :deletedBy, d.status = 'DELETED' WHERE d.id = :id")
    void softDelete(@Param("id") Long id, @Param("deletedAt") LocalDateTime deletedAt, @Param("deletedBy") Long deletedBy);

    @Modifying
    @Query("UPDATE Document d SET d.isDeleted = false, d.deletedAt = null, d.status = 'ACTIVE' WHERE d.id = :id")
    void restore(@Param("id") Long id);

    @Query("SELECT d FROM Document d WHERE d.isDeleted = true AND d.deletedAt < :cutoffDate")
    List<Document> findByIsDeletedTrueAndDeletedAtBefore(@Param("cutoffDate") LocalDateTime cutoffDate);

    @Modifying
    @Query("UPDATE Document d SET d.isDeleted = true, d.deletedAt = :deletedAt, d.deletedBy = :deletedBy WHERE d.legalCase = :legalCase AND d.isDeleted = false")
    void softDeleteByLegalCase(@Param("legalCase") LegalCase legalCase,
                               @Param("deletedAt") LocalDateTime deletedAt,
                               @Param("deletedBy") Long deletedBy);

    // ============================================
    // EDIT TRACKING FOR METADATA
    // ============================================

    @Modifying
    @Query("UPDATE Document d SET d.description = :description, d.tags = :tags, " +
            "d.isEdited = true, d.lastEditedById = :editedBy, d.lastEditedByName = :editedByName, " +
            "d.lastEditedAt = :editedAt, d.editHistory = CONCAT(COALESCE(d.editHistory, ''), :editRecord) " +
            "WHERE d.id = :id")
    void updateMetadata(@Param("id") Long id,
                        @Param("description") String description,
                        @Param("tags") String tags,
                        @Param("editedBy") Long editedBy,
                        @Param("editedByName") String editedByName,
                        @Param("editedAt") LocalDateTime editedAt,
                        @Param("editRecord") String editRecord);
}