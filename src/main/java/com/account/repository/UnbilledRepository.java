package com.account.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.account.domain.Unbilled;

@Repository
public interface UnbilledRepository extends JpaRepository<Unbilled, Long> {

	@Query(value = "SELECT * FROM unbilled u WHERE u.estimate_id =:estimateId", nativeQuery = true)
	Unbilled findByEstimateId(Long estimateId);
	
	@Query(value = "SELECT * FROM unbilled u WHERE u.company LIKE %:name%", nativeQuery = true)
	List<Unbilled> findByCompanyName(String name);

	@Query(value = "SELECT * FROM unbilled u WHERE u.estimate_id LIKE %:name%", nativeQuery = true)
	List<Unbilled> findByEstimateId(String name);

	@Query(value = "SELECT * FROM unbilled u WHERE date BETWEEN :startDate AND :endDate", nativeQuery = true)
	List<Unbilled> findAllByInBetweenDate(String startDate, String endDate);
}