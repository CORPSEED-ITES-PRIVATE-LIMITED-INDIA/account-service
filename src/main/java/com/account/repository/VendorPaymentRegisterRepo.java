package com.account.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.account.domain.VendorPaymentRegister;

@Repository
public interface VendorPaymentRegisterRepo extends JpaRepository<VendorPaymentRegister, Long> {

	@Query(value = "SELECT * FROM vendor_payment_register c where c.status =:status", nativeQuery = true)
	Page<VendorPaymentRegister> findAllByStatus(String status, Pageable pageable);

	@Query(value = "SELECT * FROM vendor_payment_register c where c.status =:status", nativeQuery = true)
	List<VendorPaymentRegister> findAllByStatus(String status);

	@Query(value = "SELECT * FROM vendor_payment_register c where c.estimate_id =:estimateId limit 1", nativeQuery = true)
	VendorPaymentRegister findByEstimateId(Long estimateId);

}
