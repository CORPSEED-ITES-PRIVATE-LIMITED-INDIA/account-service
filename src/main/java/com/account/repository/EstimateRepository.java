package com.account.repository;

import com.account.domain.estimate.Estimate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EstimateRepository extends JpaRepository<Estimate, Long> {

    Optional<Estimate> findByEstimateNumber(String estimateNumber);

    Optional<Estimate> findByPublicUuid(String publicUuid);

    boolean existsByEstimateNumber(String estimateNumber);
}