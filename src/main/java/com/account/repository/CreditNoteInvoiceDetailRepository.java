package com.account.repository;

import com.account.domain.creditNote.CreditNoteInvoiceDetail;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreditNoteInvoiceDetailRepository extends JpaRepository<CreditNoteInvoiceDetail, Long> {
}