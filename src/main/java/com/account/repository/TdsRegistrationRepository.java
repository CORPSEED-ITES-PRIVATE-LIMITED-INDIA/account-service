package com.account.repository;

import com.account.domain.PaymentReceipt;
import com.account.domain.TdsRegistration;
import com.account.domain.status.TdsStatus;
import com.account.domain.unbilled.UnbilledInvoice;
import com.account.domain.estimate.Estimate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TdsRegistrationRepository extends JpaRepository<TdsRegistration, Long> {

    Optional<TdsRegistration> findByUnbilledInvoiceAndIsDeletedFalse(UnbilledInvoice unbilledInvoice);

    Optional<TdsRegistration> findByEstimateAndIsDeletedFalse(Estimate estimate);

    List<TdsRegistration> findAllByUnbilledInvoiceAndIsDeletedFalse(UnbilledInvoice unbilledInvoice);

    List<TdsRegistration> findAllByUnbilledInvoiceAndStatusAndIsDeletedFalse(
            UnbilledInvoice unbilledInvoice,
            TdsStatus status
    );

    Optional<TdsRegistration> findByPaymentReceiptAndIsDeletedFalse(PaymentReceipt paymentReceipt);

    List<TdsRegistration> findAllByEstimateAndIsDeletedFalse(Estimate estimate);



}