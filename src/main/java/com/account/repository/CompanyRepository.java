package com.account.repository;

import com.account.domain.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CompanyRepository extends JpaRepository<Company, Long>

{

    Optional<Company> findByPanNo(String panNo);

    Optional<Company> findByUuid(String uuid);

    boolean existsByPanNoAndIdNot(String panNo, Long id);

    // If you want to find companies by onboarding status
    List<Company> findByOnboardingStatus(String onboardingStatus);

    List<Company> findByAccountsApproved(boolean approved);

    // Optional: soft delete support (if you frequently query non-deleted)
    List<Company> findByIsDeletedFalse();


}