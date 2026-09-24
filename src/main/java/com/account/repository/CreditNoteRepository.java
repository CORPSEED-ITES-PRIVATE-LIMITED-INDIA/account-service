package com.account.repository;

import com.account.domain.creditNote.CreditNote;
import com.account.domain.creditNote.CreditNoteStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CreditNoteRepository extends JpaRepository<CreditNote, Long> {

    @Query("""
            SELECT CASE WHEN COUNT(cn) > 0 THEN true ELSE false END
            FROM CreditNote cn
            WHERE cn.creditNoteNumber = :creditNoteNumber
            """)
    boolean existsByCreditNoteNumber(
            @Param("creditNoteNumber") String creditNoteNumber
    );

    @Query("""
            SELECT CASE WHEN COUNT(cn) > 0 THEN true ELSE false END
            FROM CreditNote cn
            WHERE cn.unbilledInvoice.id = :unbilledId
              AND cn.status = :status
            """)
    boolean existsByUnbilledInvoiceIdAndStatus(
            @Param("unbilledId") Long unbilledId,
            @Param("status") CreditNoteStatus status
    );

    @Query("""
            SELECT CASE WHEN COUNT(cn) > 0 THEN true ELSE false END
            FROM CreditNote cn
            WHERE cn.unbilledInvoice.id = :unbilledId
              AND cn.status IN :statuses
            """)
    boolean existsByUnbilledInvoiceIdAndStatusIn(
            @Param("unbilledId") Long unbilledId,
            @Param("statuses") List<CreditNoteStatus> statuses
    );

    @Query("""
            SELECT cn
            FROM CreditNote cn
            WHERE cn.status = :status
            """)
    Page<CreditNote> findByStatus(
            @Param("status") CreditNoteStatus status,
            Pageable pageable
    );

    @Query("""
            SELECT cn
            FROM CreditNote cn
            WHERE cn.unbilledInvoice.id = :unbilledId
            """)
    Page<CreditNote> findByUnbilledInvoiceId(
            @Param("unbilledId") Long unbilledId,
            Pageable pageable
    );

    @Query("""
            SELECT cn
            FROM CreditNote cn
            WHERE cn.unbilledInvoice.id = :unbilledId
              AND cn.status = :status
            """)
    Page<CreditNote> findByUnbilledInvoiceIdAndStatus(
            @Param("unbilledId") Long unbilledId,
            @Param("status") CreditNoteStatus status,
            Pageable pageable
    );
}