package com.account.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.account.domain.ProductEstimate;

@Repository
public interface ProductEstimateRepository extends JpaRepository<ProductEstimate, Long> {
}
