package com.account.repository;

import com.account.domain.Estimate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EstimateRepository extends JpaRepository<Estimate, Long> {

    boolean existsByOrderNumber(String orderNumber);

    Page<Estimate> findByCompanyIdAndIsDeletedFalse(Long companyId, Pageable pageable);

    Page<Estimate> findByIsDeletedFalse(Pageable pageable);

    // For search (using @Query or Specification)
    @Query("SELECT e FROM Estimate e WHERE e.isDeleted = false AND " +
            "(LOWER(e.productName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(e.orderNumber) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(e.company.name) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<Estimate> searchByQuery(@Param("query") String query, Pageable pageable);
}