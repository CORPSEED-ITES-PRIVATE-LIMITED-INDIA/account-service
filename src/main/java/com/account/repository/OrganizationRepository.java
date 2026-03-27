package com.account.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.account.domain.Organization;

import java.util.Optional;

@Repository
public interface OrganizationRepository extends JpaRepository<Organization, Long> {

    @Query(value = "SELECT * FROM organization ORDER BY COALESCE(updated_at, created_at) DESC LIMIT 1", nativeQuery = true)
    Optional<Organization> findTopOrganization();
}
