package com.account.repository;

import com.account.domain.estimate.EstimateLineItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EstimateLineItemRepository extends JpaRepository<EstimateLineItem, Long> {
    // Rarely needed — mostly managed via Estimate cascade
}