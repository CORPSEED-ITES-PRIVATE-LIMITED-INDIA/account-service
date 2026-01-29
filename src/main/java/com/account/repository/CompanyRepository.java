package com.account.repository;

import com.account.domain.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CompanyRepository extends JpaRepository<Company, Long> {

    boolean existsByNameIgnoreCaseAndIsDeletedFalse(String name);

    boolean existsByPanNoAndIsDeletedFalse(String panNo);

    Optional<Company> findByLeadId(Long leadId);

    boolean existsByLeadId(Long leadId);

    @Query("SELECT c FROM Company c WHERE c.id = :id AND c.isDeleted = false")
    Optional<Company> findByIdAndIsDeletedFalse(@Param("id") Long id);


}