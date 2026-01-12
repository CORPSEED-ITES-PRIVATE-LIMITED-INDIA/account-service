package com.account.repository;

import com.account.domain.Invoice;
import com.account.domain.UnbilledInvoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);

    Optional<Invoice> findByPublicUuid(String publicUuid);

    List<Invoice> findByUnbilledInvoice(UnbilledInvoice unbilledInvoice);

    List<Invoice> findByUnbilledInvoiceOrderByInvoiceDateDesc(UnbilledInvoice unbilledInvoice);
}