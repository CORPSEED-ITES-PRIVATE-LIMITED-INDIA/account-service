package com.account.dashboard.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.account.dashboard.domain.VendorPaymentHistory;

@Repository
public interface VendorPaymentHistoryRepository extends JpaRepository<VendorPaymentHistory, Long> {

}
