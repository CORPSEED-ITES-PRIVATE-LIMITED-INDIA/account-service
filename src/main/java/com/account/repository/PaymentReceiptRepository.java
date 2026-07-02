package com.account.repository;

import com.account.domain.PaymentReceipt;
import com.account.domain.unbilled.UnbilledInvoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentReceiptRepository extends JpaRepository<PaymentReceipt, Long> {

    Optional<PaymentReceipt> findTopByUnbilledInvoiceAndIsCancelledFalseOrderByIdAsc(UnbilledInvoice unbilledInvoice);



}