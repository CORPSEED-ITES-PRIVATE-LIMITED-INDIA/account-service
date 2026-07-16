package com.account.repository;

import com.account.domain.PaymentReceipt;
import com.account.domain.TdsRegistration;
import com.account.domain.invoice.Invoice;
import com.account.domain.status.TdsStatus;
import com.account.domain.unbilled.UnbilledInvoice;
import com.account.domain.estimate.Estimate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface TdsRegistrationRepository extends JpaRepository<TdsRegistration, Long> {


    List<TdsRegistration> findAllByUnbilledInvoiceAndIsDeletedFalse(UnbilledInvoice unbilledInvoice);

    Optional<TdsRegistration> findByPaymentReceiptAndIsDeletedFalse(PaymentReceipt paymentReceipt);

    List<TdsRegistration> findAllByEstimateAndIsDeletedFalse(Estimate estimate);

    List<TdsRegistration> findAllByInvoiceAndIsDeletedFalse(
            Invoice invoice
    );

    @Query("""
        select coalesce(sum(tds.tdsAmount), 0)
        from TdsRegistration tds
        where tds.estimate.id = :estimateId
          and tds.isDeleted = false
          and tds.status in :statuses
        """)
    BigDecimal sumRegisteredTdsAmountForEstimate(
            @Param("estimateId") Long estimateId,
            @Param("statuses") Collection<TdsStatus> statuses
    );



}