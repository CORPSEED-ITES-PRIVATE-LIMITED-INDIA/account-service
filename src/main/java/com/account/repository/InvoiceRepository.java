package com.account.repository;

import com.account.domain.Invoice;
import com.account.domain.InvoiceStatus;
import com.account.domain.PaymentReceipt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long>, JpaSpecificationExecutor<Invoice> {


    @Query("""
    SELECT i FROM Invoice i
    WHERE i.isCancelled = false
    AND (:status IS NULL OR i.status = :status)
    AND (:createdById IS NULL OR i.createdBy.id = :createdById)
    """)
    Page<Invoice> findInvoicesAndIsCancelledFalse(
            @Param("status") InvoiceStatus status,
            @Param("createdById") Long createdById,
            Pageable pageable
    );

    List<Invoice> findByUnbilledInvoiceIdAndIsCancelledFalse(Long unbilledInvoiceId);

    @Query("""
        SELECT COUNT(i) FROM Invoice i
        WHERE i.isCancelled = false
            AND(:status IS NULL OR i.status = :status)
            AND (:createdById IS NULL OR i.createdBy.id = :createdById)
        """)
    long countInvoices(
            @Param("status") InvoiceStatus status,
            @Param("createdById") Long createdById
    );

    @Query("""
        SELECT i FROM Invoice i
        LEFT JOIN i.unbilledInvoice u
        LEFT JOIN u.company c
        WHERE  i.isCancelled=false AND (:invoiceNumber IS NULL OR LOWER(i.invoiceNumber) LIKE LOWER(CONCAT('%', :invoiceNumber, '%')))
        AND (:companyName IS NULL OR LOWER(c.name) LIKE LOWER(CONCAT('%', :companyName, '%')))
        """)
    Page<Invoice> searchInvoices(
            @Param("invoiceNumber") String invoiceNumber,
            @Param("companyName") String companyName,
            Pageable pageable
    );

    @Query("""
        SELECT COUNT(i) FROM Invoice i
        LEFT JOIN i.unbilledInvoice u
        LEFT JOIN u.company c
        WHERE i.isCancelled=false AND (:invoiceNumber IS NULL OR LOWER(i.invoiceNumber) LIKE LOWER(CONCAT('%', :invoiceNumber, '%')))
        AND (:companyName IS NULL OR LOWER(c.name) LIKE LOWER(CONCAT('%', :companyName, '%')))
        """)
    long countSearchInvoices(
            @Param("invoiceNumber") String invoiceNumber,
            @Param("companyName") String companyName
    );

    boolean existsByTriggeringPayment(PaymentReceipt p);
}