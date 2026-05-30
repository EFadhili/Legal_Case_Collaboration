package com.legalcase.repository;

import com.legalcase.entity.AIInteraction;
import com.legalcase.entity.LegalCase;
import com.legalcase.entity.User;
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
public interface AIInteractionRepository extends JpaRepository<AIInteraction, Long> {

    // ============================================
    // FIND WITH ENTITY GRAPH (prevent LazyInitialization)
    // ============================================

    @Override
    @EntityGraph(attributePaths = {"user", "legalCase", "legalCase.owner"})
    Optional<AIInteraction> findById(Long id);

    @EntityGraph(attributePaths = {"user", "legalCase", "legalCase.owner"})
    Optional<AIInteraction> findByInteractionNumberAndIsDeletedFalse(String interactionNumber);

    @EntityGraph(attributePaths = {"user", "legalCase", "legalCase.owner"})
    Optional<AIInteraction> findByIdAndIsDeletedFalse(Long id);

    // ============================================
    // USER HISTORY (with soft delete filter)
    // ============================================

    @EntityGraph(attributePaths = {"user", "legalCase", "legalCase.owner"})
    @Query("SELECT a FROM AIInteraction a WHERE a.user = :user AND a.isDeleted = false ORDER BY a.createdAt DESC")
    List<AIInteraction> findByUserOrderByCreatedAtDesc(@Param("user") User user);

    @EntityGraph(attributePaths = {"user", "legalCase", "legalCase.owner"})
    @Query("SELECT a FROM AIInteraction a WHERE a.user = :user AND a.isDeleted = false")
    Page<AIInteraction> findByUser(@Param("user") User user, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "legalCase", "legalCase.owner"})
    @Query("SELECT a FROM AIInteraction a WHERE a.user = :user AND a.isDeleted = false AND a.queryType = :queryType ORDER BY a.createdAt DESC")
    List<AIInteraction> findByUserAndQueryType(@Param("user") User user, @Param("queryType") String queryType);

    // ============================================
    // CASE HISTORY (with permission filtering)
    // ============================================

    @EntityGraph(attributePaths = {"user", "legalCase", "legalCase.owner"})
    @Query("SELECT a FROM AIInteraction a WHERE a.legalCase = :legalCase AND a.isDeleted = false ORDER BY a.createdAt DESC")
    List<AIInteraction> findByLegalCaseOrderByCreatedAtDesc(@Param("legalCase") LegalCase legalCase);

    @EntityGraph(attributePaths = {"user", "legalCase", "legalCase.owner"})
    @Query("SELECT a FROM AIInteraction a WHERE a.legalCase = :legalCase AND a.isDeleted = false")
    Page<AIInteraction> findByLegalCase(@Param("legalCase") LegalCase legalCase, Pageable pageable);

    // ============================================
    // SEARCH METHODS (with permission scoping)
    // ============================================

    // User searches their own interactions
    @EntityGraph(attributePaths = {"user", "legalCase", "legalCase.owner"})
    @Query("SELECT a FROM AIInteraction a WHERE a.user = :user AND a.isDeleted = false AND " +
            "(LOWER(a.interactionNumber) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(a.userPrompt) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(a.aiResponse) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(CAST(a.queryType AS string)) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) " +
            "ORDER BY a.createdAt DESC")
    Page<AIInteraction> searchByUser(@Param("user") User user,
                                     @Param("searchTerm") String searchTerm,
                                     Pageable pageable);

    // Search interactions within a specific case
    @EntityGraph(attributePaths = {"user", "legalCase", "legalCase.owner"})
    @Query("SELECT a FROM AIInteraction a WHERE a.legalCase = :legalCase AND a.isDeleted = false AND " +
            "(LOWER(a.interactionNumber) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(a.userPrompt) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(a.aiResponse) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(CAST(a.queryType AS string)) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) " +
            "ORDER BY a.createdAt DESC")
    Page<AIInteraction> searchByLegalCase(@Param("legalCase") LegalCase legalCase,
                                          @Param("searchTerm") String searchTerm,
                                          Pageable pageable);

    // Admin global search (all interactions, including deleted if needed)
    @EntityGraph(attributePaths = {"user", "legalCase", "legalCase.owner"})
    @Query("SELECT a FROM AIInteraction a WHERE a.isDeleted = false AND " +
            "(LOWER(a.interactionNumber) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(a.userPrompt) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(a.aiResponse) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(CAST(a.queryType AS string)) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) " +
            "ORDER BY a.createdAt DESC")
    Page<AIInteraction> adminGlobalSearch(@Param("searchTerm") String searchTerm, Pageable pageable);

    // Admin can also search deleted interactions
    @EntityGraph(attributePaths = {"user", "legalCase", "legalCase.owner"})
    @Query("SELECT a FROM AIInteraction a WHERE a.isDeleted = true AND " +
            "(LOWER(a.interactionNumber) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(a.userPrompt) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(a.aiResponse) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) " +
            "ORDER BY a.deletedAt DESC")
    Page<AIInteraction> adminSearchDeleted(@Param("searchTerm") String searchTerm, Pageable pageable);

    // ============================================
    // STATISTICS & AGGREGATION
    // ============================================

    @Query("SELECT AVG(a.userRating) FROM AIInteraction a WHERE a.user = :user AND a.userRating IS NOT NULL AND a.isDeleted = false")
    Double getAverageUserRating(@Param("user") User user);

    long countByUserAndIsDeletedFalse(User user);

    @Query("SELECT COUNT(a) FROM AIInteraction a WHERE a.legalCase = :legalCase AND a.isDeleted = false")
    long countByLegalCase(@Param("legalCase") LegalCase legalCase);

    // ============================================
    // SOFT DELETE METHODS
    // ============================================

    @Modifying
    @Query("UPDATE AIInteraction a SET a.isDeleted = true, a.deletedAt = :deletedAt, a.deletedBy = :deletedBy, a.deletedReason = :deletedReason WHERE a.id = :id")
    void softDelete(@Param("id") Long id,
                    @Param("deletedAt") LocalDateTime deletedAt,
                    @Param("deletedBy") Long deletedBy,
                    @Param("deletedReason") String deletedReason);

    @Modifying
    @Query("UPDATE AIInteraction a SET a.isDeleted = false, a.deletedAt = null WHERE a.id = :id")
    void restore(@Param("id") Long id);

    @Query("SELECT a FROM AIInteraction a WHERE a.isDeleted = true AND a.deletedAt < :cutoffDate")
    List<AIInteraction> findByIsDeletedTrueAndDeletedAtBefore(@Param("cutoffDate") LocalDateTime cutoffDate);

    // ============================================
    // RATING UPDATE WITH EDIT TRACKING
    // ============================================

    @Modifying
    @Query("UPDATE AIInteraction a SET a.userRating = :rating, a.ratingUpdatedAt = :updatedAt, a.ratingUpdatedBy = :updatedBy, a.ratingHistory = CONCAT(COALESCE(a.ratingHistory, ''), :historyRecord) WHERE a.id = :id")
    void updateRating(@Param("id") Long id,
                      @Param("rating") Integer rating,
                      @Param("updatedAt") LocalDateTime updatedAt,
                      @Param("updatedBy") Long updatedBy,
                      @Param("historyRecord") String historyRecord);
}