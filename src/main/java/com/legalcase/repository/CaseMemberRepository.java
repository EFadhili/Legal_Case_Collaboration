package com.legalcase.repository;

import com.legalcase.entity.CaseMember;
import com.legalcase.entity.LegalCase;
import com.legalcase.entity.User;
import com.legalcase.enums.CaseMemberRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CaseMemberRepository extends JpaRepository<CaseMember, Long> {

    // Basic queries
    List<CaseMember> findByLegalCase(LegalCase legalCase);

    List<CaseMember> findByUser(User user);

    Optional<CaseMember> findByLegalCaseAndUser(LegalCase legalCase, User user);

    boolean existsByLegalCaseAndUser(LegalCase legalCase, User user);

    List<CaseMember> findByLegalCaseAndRole(LegalCase legalCase, CaseMemberRole role);

    void deleteByLegalCaseAndUser(LegalCase legalCase, User user);

    // ===== JOIN FETCH methods to prevent LazyInitializationException =====

    @Query("SELECT DISTINCT cm FROM CaseMember cm " +
            "LEFT JOIN FETCH cm.user " +
            "LEFT JOIN FETCH cm.legalCase c " +
            "LEFT JOIN FETCH c.owner " +
            "WHERE cm.legalCase = :legalCase")
    List<CaseMember> findByLegalCaseWithDetails(@Param("legalCase") LegalCase legalCase);

    @Query("SELECT DISTINCT cm FROM CaseMember cm " +
            "LEFT JOIN FETCH cm.user " +
            "LEFT JOIN FETCH cm.legalCase " +
            "WHERE cm.user = :user")
    List<CaseMember> findByUserWithDetails(@Param("user") User user);

    @Query("SELECT DISTINCT cm FROM CaseMember cm " +
            "LEFT JOIN FETCH cm.user " +
            "LEFT JOIN FETCH cm.legalCase " +
            "WHERE cm.legalCase = :legalCase AND cm.role = :role")
    List<CaseMember> findByLegalCaseAndRoleWithDetails(@Param("legalCase") LegalCase legalCase,
                                                       @Param("role") CaseMemberRole role);

    @Query("SELECT CASE WHEN COUNT(cm) > 0 THEN true ELSE false END FROM CaseMember cm WHERE cm.legalCase.id = :caseId AND cm.user.id = :userId")
    boolean existsByLegalCaseIdAndUserId(@Param("caseId") Long caseId, @Param("userId") Long userId);
}