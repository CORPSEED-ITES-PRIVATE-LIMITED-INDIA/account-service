package com.account.repository;

import com.account.domain.estimate.Estimate;
import com.account.domain.estimate.EstimateStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;


@Repository
public interface EstimateRepository extends JpaRepository<Estimate, Long>, JpaSpecificationExecutor <Estimate> {

    @Query("""
        SELECT e FROM Estimate e
        WHERE e.isCancelled=false AND e.leadId = :leadId
          AND e.isDeleted = false
        ORDER BY e.createdAt DESC
        """)
    List<Estimate> findByLeadIdAndIsDeletedFalseOrderByCreatedAtDesc(@Param("leadId") Long leadId);

    // Keep the exact same method name as before
    @Query("""
        SELECT e FROM Estimate e
        WHERE e.isCancelled=false AND e.company.id = :companyId
          AND e.isDeleted = false
        ORDER BY e.createdAt DESC
        """)
    List<Estimate> findByCompanyIdAndIsDeletedFalseOrderByCreatedAtDesc(@Param("companyId") Long companyId);

    boolean existsByLeadIdAndIsDeletedFalseAndIsCancelledFalseAndStatusNot(Long leadId, EstimateStatus status);


    List<Estimate> findByCompanyIdAndUnitIdAndIsDeletedFalseAndIsCancelledFalseOrderByCreatedAtDesc(
            Long companyId,
            Long unitId
    );

    Optional<Estimate> findByProposalIdAndIsDeletedFalseAndIsCancelledFalse(Long proposalId);

    /**
     * Find Estimate by Estimate Number using Native Query
     */
    @Query(value = """
    SELECT * FROM estimate 
    WHERE estimate_number = :estimateNumber 
      AND is_deleted = false 
    LIMIT 1
    """,
            nativeQuery = true)
    Optional<Estimate> findByEstimateNumberAndIsDeletedFalse(@Param("estimateNumber") String estimateNumber);



}