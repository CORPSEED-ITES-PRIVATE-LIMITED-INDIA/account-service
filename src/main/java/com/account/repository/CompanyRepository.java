package com.account.repository;

import com.account.domain.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CompanyRepository extends JpaRepository<Company, Long> {

    // These two lines solve your "cannot resolve method" errors
    boolean existsByNameIgnoreCaseAndIsDeletedFalse(String name);

    boolean existsByPanNoAndIsDeletedFalse(String panNo);

    // ────────────────────────────────────────────────
    // Very useful additions (strongly recommended):
    // ────────────────────────────────────────────────

    // Find by PAN (most common duplicate check)
    Optional<Company> findByPanNoAndIsDeletedFalse(String panNo);

    // Find active company by exact name (case-sensitive)
    Optional<Company> findByNameAndIsDeletedFalse(String name);

    // Sometimes needed during updates
    boolean existsByPanNoAndIdNotAndIsDeletedFalse(String panNo, Long id);

    boolean existsByNameIgnoreCaseAndIdNotAndIsDeletedFalse(String name, Long id);

    // If you ever soft-delete companies and want to list only active ones
    // List<Company> findAllByIsDeletedFalse();

    // Optional: if you use UUID later
    // Optional<Company> findByUuidAndIsDeletedFalse(String uuid);
}