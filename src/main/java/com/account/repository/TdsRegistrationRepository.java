package com.account.repository;

import com.account.domain.TdsRegistration;
import com.account.domain.TdsStatus;
import com.account.domain.UnbilledInvoice;
import com.account.domain.estimate.Estimate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TdsRegistrationRepository extends JpaRepository<TdsRegistration, Long> {

    Optional<TdsRegistration> findByUnbilledInvoiceAndIsDeletedFalse(UnbilledInvoice unbilledInvoice);

    Optional<TdsRegistration> findByEstimateAndIsDeletedFalse(Estimate estimate);

    boolean existsByUnbilledInvoiceAndIsDeletedFalse(UnbilledInvoice unbilledInvoice);

    boolean existsByEstimateAndIsDeletedFalse(Estimate estimate);

    Optional<TdsRegistration> findByUnbilledInvoiceAndStatusAndIsDeletedFalse(
            UnbilledInvoice unbilledInvoice,
            TdsStatus status
    );
}