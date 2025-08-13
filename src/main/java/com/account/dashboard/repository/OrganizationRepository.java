package com.account.dashboard.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.account.dashboard.domain.Organization;

@Repository
public interface OrganizationRepository extends JpaRepository<Organization, Long> {

	@Query(value = "SELECT * FROM organization o WHERE o.name =:name limit 1", nativeQuery = true)
	Organization findByName(String name);

}
