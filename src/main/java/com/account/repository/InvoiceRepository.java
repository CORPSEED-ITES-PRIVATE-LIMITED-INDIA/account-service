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
import java.util.Optional;

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


    @Query("""
        SELECT COUNT(i) FROM Invoice i
        WHERE i.isCancelled = false
          AND (:status IS NULL OR i.status = :status)
          AND (:createdById IS NULL OR i.createdBy.id = :createdById)
    """)
    long countInvoices(
            @Param("status") InvoiceStatus status,
            @Param("createdById") Long createdById
    );

    @Query("""
        SELECT i
        FROM Invoice i
        LEFT JOIN i.unbilledInvoice u
        LEFT JOIN u.company c
        WHERE i.isCancelled = false
          AND (:invoiceNumber IS NULL OR LOWER(i.invoiceNumber) LIKE LOWER(CONCAT('%', :invoiceNumber, '%')))
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
        WHERE i.isCancelled = false
          AND (:invoiceNumber IS NULL OR LOWER(i.invoiceNumber) LIKE LOWER(CONCAT('%', :invoiceNumber, '%')))
          AND (:companyName IS NULL OR LOWER(c.name) LIKE LOWER(CONCAT('%', :companyName, '%')))
    """)
    long countSearchInvoices(
            @Param("invoiceNumber") String invoiceNumber,
            @Param("companyName") String companyName
    );

    boolean existsByTriggeringPayment(PaymentReceipt p);

    /**
     * Find Invoice by Invoice Number using Native Query
     */
    @Query(value = """
    SELECT * FROM invoice 
    WHERE invoice_number = :invoiceNumber 
      AND is_cancelled = false 
    LIMIT 1
    """,
            nativeQuery = true)
    Optional<Invoice> findByInvoiceNumberAndIsCancelledFalse(@Param("invoiceNumber") String invoiceNumber);


    @Query("""
        SELECT i
        FROM Invoice i
        JOIN i.unbilledInvoice u
        LEFT JOIN u.createdBy cb
        LEFT JOIN u.approvedBy ab
        WHERE i.isCancelled = false
          AND u.isCancelled = false
          AND (:unbilledId IS NULL OR u.id = :unbilledId)
          AND (
                :visibleUserId IS NULL
                OR cb.id = :visibleUserId
                OR ab.id = :visibleUserId
              )
        """)
    Page<Invoice> findInvoicesByUnbilledAndUserAccess(
            @Param("visibleUserId") Long visibleUserId,
            @Param("unbilledId") Long unbilledId,
            Pageable pageable
    );


    @Query("""
        SELECT COUNT(i)
        FROM Invoice i
        JOIN i.unbilledInvoice u
        LEFT JOIN u.createdBy cb
        LEFT JOIN u.approvedBy ab
        WHERE i.isCancelled = false
          AND u.isCancelled = false
          AND (:unbilledId IS NULL OR u.id = :unbilledId)
          AND (
                :visibleUserId IS NULL
                OR cb.id = :visibleUserId
                OR ab.id = :visibleUserId
              )
        """)
    long countInvoicesByUnbilledAndUserAccess(
            @Param("visibleUserId") Long visibleUserId,
            @Param("unbilledId") Long unbilledId
    );
}