package com.account.repository;

import com.account.domain.PaymentReceipt;
import com.account.domain.UnbilledInvoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentReceiptRepository extends JpaRepository<PaymentReceipt, Long> {

    Optional<PaymentReceipt> findTopByUnbilledInvoiceOrderByIdAsc(UnbilledInvoice unbilledInvoice);

    @Query("SELECT pr FROM PaymentReceipt pr " +
            "WHERE pr.unbilledInvoice.id = :unbilledId " +
            "AND NOT EXISTS (" +
            "   SELECT i FROM Invoice i " +
            "   WHERE i.triggeringPayment.id = pr.id" +
            ") " +
            "ORDER BY pr.paymentDate ASC")
    List<PaymentReceipt> findUninvoicedPaymentsByUnbilledId(@Param("unbilledId") Long unbilledId);


}