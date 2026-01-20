package com.account.repository;

import com.account.domain.estimate.Estimate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EstimateRepository extends JpaRepository<Estimate, Long> {

    @Query("""
        SELECT e FROM Estimate e
        WHERE e.leadId = :leadId
          AND e.isDeleted = false
        ORDER BY e.createdAt DESC
        """)
    List<Estimate> findByLeadIdAndIsDeletedFalseOrderByCreatedAtDesc(@Param("leadId") Long leadId);

    // Keep the exact same method name as before
    @Query("""
        SELECT e FROM Estimate e
        WHERE e.company.id = :companyId
          AND e.isDeleted = false
        ORDER BY e.createdAt DESC
        """)
    List<Estimate> findByCompanyIdAndIsDeletedFalseOrderByCreatedAtDesc(@Param("companyId") Long companyId);

    Page<Estimate> findByCreatedById(Long requestingUserId, Pageable pageable);

    /**
     * Count all non-deleted estimates (for admins)
     */
    long countByIsDeletedFalse();

    /**
     * Count non-deleted estimates created by a specific user
     */
    long countByCreatedByIdAndIsDeletedFalse(Long createdById);
}