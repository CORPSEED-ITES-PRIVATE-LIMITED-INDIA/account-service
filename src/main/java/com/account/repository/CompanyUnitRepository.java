package com.account.repository;

import com.account.domain.CompanyUnit;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CompanyUnitRepository extends JpaRepository<CompanyUnit, Long>
        {
            Optional<CompanyUnit> findByLeadId(Long leadId);   // ← change to this

}