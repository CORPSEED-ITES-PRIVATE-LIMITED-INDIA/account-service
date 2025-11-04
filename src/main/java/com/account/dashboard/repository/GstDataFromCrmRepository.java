package com.account.dashboard.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.account.dashboard.domain.GstDataFromCrm;
import com.account.dashboard.domain.GstDetails;

@Repository
public interface GstDataFromCrmRepository extends JpaRepository<GstDataFromCrm, Long> {

	@Query(value = "SELECT * FROM gst_data_from_crm gd WHERE gd.create_date BETWEEN :d1 AND :d2", nativeQuery = true)
	Page<GstDataFromCrm> findAllByDateInBetween(String d1, String d2, Pageable pageable);
	
	@Query(value = "SELECT * FROM gst_data_from_crm gd WHERE gd.create_date BETWEEN :d1 AND :d2", nativeQuery = true)
	List<GstDataFromCrm> findAllByDateInBetween(String d1, String d2);
	
	@Query(value = "SELECT count(*) FROM gst_data_from_crm gd WHERE gd.create_date BETWEEN :d1 AND :d2", nativeQuery = true)
	Long findCountByDateInBetween(String d1, String d2);

}
