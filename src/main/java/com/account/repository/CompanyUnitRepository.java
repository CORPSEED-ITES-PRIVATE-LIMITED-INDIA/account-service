package com.account.repository;

import com.account.domain.CompanyUnit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CompanyUnitRepository extends JpaRepository<CompanyUnit, Long> {

    // Already present – keep if you really use lead-based lookup
    Optional<CompanyUnit> findByLeadId(Long leadId);

    // ───────────────────────────────────────────────
    // Most important for your service logic (create/update + duplicate checks)
    // ───────────────────────────────────────────────

    /**
     * Find active (not deleted) unit by ID and belonging to specific company
     * Used when updating an existing unit
     */
    Optional<CompanyUnit> findByIdAndCompanyIdAndIsDeletedFalse(Long id, Long companyId);

    /**
     * Check if a GSTIN is already used by any **active** unit in the same company
     * (very important for GST uniqueness per company)
     */
    boolean existsByGstNoAndCompanyIdAndIsDeletedFalse(String gstNo, Long companyId);

    /**
     * Optional: same check but ignoring the current unit (useful during UPDATE)
     */
    @Query("SELECT COUNT(u) > 0 FROM CompanyUnit u " +
            "WHERE u.gstNo = :gstNo " +
            "  AND u.company.id = :companyId " +
            "  AND u.isDeleted = false " +
            "  AND u.id <> :excludeId")
    boolean existsByGstNoAndCompanyIdAndIsDeletedFalseAndIdNot(
            String gstNo, Long companyId, Long excludeId);

    /**
     * Find all active units for a company
     * (useful for listing branches/GST units in UI)
     */
    List<CompanyUnit> findAllByCompanyIdAndIsDeletedFalse(Long companyId);

    /**
     * Find active unit by exact unitName in a company
     * (can be used instead of stream filtering in service)
     */
    Optional<CompanyUnit> findByUnitNameAndCompanyIdAndIsDeletedFalse(
            String unitName, Long companyId);

    /**
     * Optional: case-insensitive name search (if you want to allow fuzzy checks later)
     */
    @Query("SELECT u FROM CompanyUnit u " +
            "WHERE LOWER(u.unitName) = LOWER(:unitName) " +
            "  AND u.company.id = :companyId " +
            "  AND u.isDeleted = false")
    Optional<CompanyUnit> findByUnitNameIgnoreCaseAndCompanyIdAndIsDeletedFalse(
            String unitName, Long companyId);

    // ───────────────────────────────────────────────
    // If you ever need bulk / reporting queries
    // ───────────────────────────────────────────────

    long countByCompanyIdAndIsDeletedFalse(Long companyId);

    // Example of soft-delete (mark as deleted instead of hard delete)
    @Query("UPDATE CompanyUnit u SET u.isDeleted = true, u.updatedAt = CURRENT_TIMESTAMP " +
            "WHERE u.id = :id AND u.company.id = :companyId")
    int softDeleteByIdAndCompanyId(Long id, Long companyId);
}