package com.account.dashboard.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.account.dashboard.domain.PaymentRegister;
import com.account.dashboard.domain.ProductEstimate;

@Repository
public interface ProductEstimateRepository extends JpaRepository<ProductEstimate, Long> {
}
