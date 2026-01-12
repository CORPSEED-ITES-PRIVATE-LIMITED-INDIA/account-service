package com.account.repository;

import com.account.domain.PaymentReceipt;
import com.account.domain.UnbilledInvoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentReceiptRepository extends JpaRepository<PaymentReceipt, Long> {

    List<PaymentReceipt> findByUnbilledInvoice(UnbilledInvoice unbilledInvoice);

    List<PaymentReceipt> findByUnbilledInvoiceOrderByPaymentDateDesc(UnbilledInvoice unbilledInvoice);
}