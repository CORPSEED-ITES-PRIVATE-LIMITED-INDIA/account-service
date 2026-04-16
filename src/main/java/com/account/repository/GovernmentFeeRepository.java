package com.account.repository;

import com.account.domain.GovernmentFee;
import com.account.domain.UnbilledInvoice;
import com.account.domain.estimate.Estimate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GovernmentFeeRepository extends JpaRepository<GovernmentFee, Long> {

    Optional<GovernmentFee> findByEstimate(Estimate estimate);

    Optional<GovernmentFee> findByUnbilledInvoice(UnbilledInvoice unbilledInvoice);

    boolean existsByEstimate(Estimate estimate);

    boolean existsByUnbilledInvoice(UnbilledInvoice unbilledInvoice);

    Optional<GovernmentFee> findByEstimateId(Long estimateId);

    Optional<GovernmentFee> findByUnbilledInvoiceId(Long unbilledInvoiceId);
}