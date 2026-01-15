package com.account.repository;

import com.account.domain.Invoice;
import com.account.domain.InvoiceStatus;
import com.account.domain.UnbilledInvoice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    Invoice findByInvoiceNumber(String invoiceNumber);

    Invoice findByPublicUuid(String publicUuid);

    @Query("""
        SELECT i FROM Invoice i
        WHERE (:status IS NULL OR i.status = :status)
        AND (:createdById IS NULL OR i.createdBy.id = :createdById)
        """)
    Page<Invoice> findInvoices(
            @Param("status") InvoiceStatus status,
            @Param("createdById") Long createdById,
            Pageable pageable
    );

    @Query("""
        SELECT COUNT(i) FROM Invoice i
        WHERE (:status IS NULL OR i.status = :status)
        AND (:createdById IS NULL OR i.createdBy.id = :createdById)
        """)
    long countInvoices(
            @Param("status") InvoiceStatus status,
            @Param("createdById") Long createdById
    );
}