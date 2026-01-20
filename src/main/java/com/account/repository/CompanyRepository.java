package com.account.repository;

import com.account.domain.Company;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CompanyRepository extends JpaRepository<Company, Long> {

    boolean existsByNameIgnoreCaseAndIsDeletedFalse(String name);

    boolean existsByPanNoAndIsDeletedFalse(String panNo);

    // Fixed versions ↓
    Optional<Company> findByLeadId(Long leadId);           // ← change here

    boolean existsByLeadId(Long leadId);                   // ← change here too if you use it

}