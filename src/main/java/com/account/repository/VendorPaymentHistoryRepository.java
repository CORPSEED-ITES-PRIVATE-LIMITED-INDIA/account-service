package com.account.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.account.domain.VendorPaymentHistory;

@Repository
public interface VendorPaymentHistoryRepository extends JpaRepository<VendorPaymentHistory, Long> {

	List<VendorPaymentHistory> findAllByVendorPaymentRegisterId(Long id);

}
