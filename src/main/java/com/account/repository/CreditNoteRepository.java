package com.account.repository;

import com.account.domain.creditNote.CreditNote;
import com.account.domain.creditNote.CreditNoteStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CreditNoteRepository extends JpaRepository<CreditNote, Long> {

    boolean existsByCreditNoteNumber(String creditNoteNumber);

    boolean existsByUnbilledInvoiceIdAndStatus(Long unbilledId, CreditNoteStatus status);

    Optional<CreditNote> findByUnbilledInvoiceIdAndStatus(Long unbilledId, CreditNoteStatus status);

    Page<CreditNote> findByStatus(CreditNoteStatus status, Pageable pageable);

    Page<CreditNote> findByUnbilledInvoiceId(Long unbilledId, Pageable pageable);

    Page<CreditNote> findByUnbilledInvoiceIdAndStatus(
            Long unbilledId,
            CreditNoteStatus status,
            Pageable pageable
    );
}