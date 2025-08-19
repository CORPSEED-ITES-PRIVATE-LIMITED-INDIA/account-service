package com.account.dashboard.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.account.dashboard.domain.Unbilled;

@Repository
public interface UnbilledRepository extends JpaRepository<Unbilled, Long> {

	@Query(value = "SELECT * FROM unbilled u WHERE u.estimate_id =:estimateId", nativeQuery = true)
	Unbilled findByEstimateId(Long estimateId);
}