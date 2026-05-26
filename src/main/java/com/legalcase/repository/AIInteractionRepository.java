package com.legalcase.repository;

import com.legalcase.entity.AIInteraction;
import com.legalcase.entity.LegalCase;
import com.legalcase.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AIInteractionRepository extends JpaRepository<AIInteraction, Long> {

    List<AIInteraction> findByUserOrderByCreatedAtDesc(User user);

    Page<AIInteraction> findByUser(User user, Pageable pageable);

    List<AIInteraction> findByLegalCaseOrderByCreatedAtDesc(LegalCase legalCase);

    @Query("SELECT a FROM AIInteraction a WHERE a.user = :user AND a.queryType = :queryType ORDER BY a.createdAt DESC")
    List<AIInteraction> findByUserAndQueryType(@Param("user") User user, @Param("queryType") String queryType);

    @Query("SELECT AVG(a.userRating) FROM AIInteraction a WHERE a.user = :user AND a.userRating IS NOT NULL")
    Double getAverageUserRating(@Param("user") User user);

    long countByUser(User user);

    @Query("SELECT DISTINCT a FROM AIInteraction a " +
            "LEFT JOIN FETCH a.user " +
            "LEFT JOIN FETCH a.legalCase " +
            "WHERE a.user = :user " +
            "ORDER BY a.createdAt DESC")
    List<AIInteraction> findByUserWithDetails(@Param("user") User user);

    @Query("SELECT DISTINCT a FROM AIInteraction a " +
            "LEFT JOIN FETCH a.user " +
            "LEFT JOIN FETCH a.legalCase c " +
            "LEFT JOIN FETCH c.owner " +
            "WHERE a.legalCase = :legalCase " +
            "ORDER BY a.createdAt DESC")
    List<AIInteraction> findByLegalCaseWithDetails(@Param("legalCase") LegalCase legalCase);

    @Query("SELECT DISTINCT a FROM AIInteraction a " +
            "LEFT JOIN FETCH a.user " +
            "LEFT JOIN FETCH a.legalCase c " +
            "LEFT JOIN FETCH c.owner " +
            "WHERE a.user = :user")
    Page<AIInteraction> findByUserWithDetailsPage(@Param("user") User user, Pageable pageable);

}

