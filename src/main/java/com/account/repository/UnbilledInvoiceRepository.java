package com.account.repository;

import com.account.domain.UnbilledInvoice;
import com.account.domain.estimate.Estimate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UnbilledInvoiceRepository extends JpaRepository<UnbilledInvoice, Long> {

    Optional<UnbilledInvoice> findByEstimate(Estimate estimate);

    Optional<UnbilledInvoice> findByUnbilledNumber(String unbilledNumber);

    Optional<UnbilledInvoice> findByPublicUuid(String publicUuid);
}