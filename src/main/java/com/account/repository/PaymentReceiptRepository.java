package com.account.repository;

import com.account.domain.PaymentReceipt;
import com.account.domain.UnbilledInvoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentReceiptRepository extends JpaRepository<PaymentReceipt, Long> {

    List<PaymentReceipt> findByUnbilledInvoice(UnbilledInvoice unbilledInvoice);

    boolean existsByTransactionReference(String transactionReference);
    Optional<PaymentReceipt> findTopByUnbilledInvoiceOrderByIdAsc(UnbilledInvoice unbilledInvoice);


    List<PaymentReceipt> findByUnbilledInvoiceOrderByPaymentDateDesc(UnbilledInvoice unbilledInvoice);
}