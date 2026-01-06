package com.account.repository;

import com.account.domain.Company;
import com.account.domain.CompanyUnit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CompanyUnitRepository extends JpaRepository<CompanyUnit, Long>
        {

    List<CompanyUnit> findByCompany(Company company);

    List<CompanyUnit> findByCompanyId(Long companyId);

    Optional<CompanyUnit> findByGstNo(String gstNo);

    // Useful when syncing from lead system with same IDs
    List<CompanyUnit> findByCompanyIdAndIdIn(Long companyId, Iterable<Long> ids);

    // Soft delete support
    List<CompanyUnit> findByCompanyIdAndIsDeletedFalse(Long companyId);

    @Modifying
    @Query("UPDATE CompanyUnit cu SET cu.isDeleted = true WHERE cu.company.id = :companyId")
    void softDeleteByCompanyId(Long companyId);

    // If you ever need to hard-delete units of a company
    @Modifying
    @Query("DELETE FROM CompanyUnit cu WHERE cu.company.id = :companyId")
    void deleteByCompanyId(Long companyId);

    boolean existsByGstNoAndIdNot(String gstNo, Long id);
}