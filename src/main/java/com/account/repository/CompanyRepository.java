package com.account.repository;

import com.account.domain.Company;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CompanyRepository extends JpaRepository<Company, Long> {

    boolean existsByNameIgnoreCaseAndIsDeletedFalse(String name);

    boolean existsByPanNoAndIsDeletedFalse(String panNo);

    Optional<Company> findByLeadId(Long leadId);

    boolean existsByLeadId(Long leadId);

}