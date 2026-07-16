package com.account.repository;

import com.account.domain.invoice.Invoice;
import com.account.domain.invoice.InvoiceOrigin;
import com.account.domain.invoice.InvoicePaymentStatus;
import com.account.domain.status.InvoiceStatus;
import com.account.domain.PaymentReceipt;
import com.account.dto.dashboard.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
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



    @Query("""
    SELECT i
    FROM Invoice i
    LEFT JOIN i.unbilledInvoice u
    LEFT JOIN u.createdBy unbilledCreatedBy
    LEFT JOIN u.approvedBy unbilledApprovedBy
    LEFT JOIN i.createdBy invoiceCreatedBy
    WHERE i.isCancelled = false
      AND (:visibleUserId IS NULL
            OR invoiceCreatedBy.id = :visibleUserId
            OR unbilledCreatedBy.id = :visibleUserId
            OR unbilledApprovedBy.id = :visibleUserId
          )
      AND (:createdByUserId IS NULL OR unbilledCreatedBy.id = :createdByUserId)
      AND (:status IS NULL OR i.status = :status)
      AND (:fromDate IS NULL OR i.invoiceDate >= :fromDate)
      AND (:toDate IS NULL OR i.invoiceDate <= :toDate)
    ORDER BY i.createdAt DESC
""")
    List<Invoice> findInvoiceReport(
            @Param("visibleUserId") Long visibleUserId,
            @Param("createdByUserId") Long createdByUserId,
            @Param("status") InvoiceStatus status,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate
    );

    @Query("""
    SELECT COUNT(i)
    FROM Invoice i
    LEFT JOIN i.unbilledInvoice u
    LEFT JOIN u.createdBy unbilledCreatedBy
    LEFT JOIN u.approvedBy unbilledApprovedBy
    LEFT JOIN i.createdBy invoiceCreatedBy
    WHERE i.isCancelled = false
      AND (:visibleUserId IS NULL
            OR invoiceCreatedBy.id = :visibleUserId
            OR unbilledCreatedBy.id = :visibleUserId
            OR unbilledApprovedBy.id = :visibleUserId
          )
      AND (:createdByUserId IS NULL OR unbilledCreatedBy.id = :createdByUserId)
      AND (:status IS NULL OR i.status = :status)
      AND (:fromDate IS NULL OR i.invoiceDate >= :fromDate)
      AND (:toDate IS NULL OR i.invoiceDate <= :toDate)
""")
    long countInvoiceReport(
            @Param("visibleUserId") Long visibleUserId,
            @Param("createdByUserId") Long createdByUserId,
            @Param("status") InvoiceStatus status,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate
    );

    @Query("""
            SELECT new com.account.dto.dashboard.TopSellingServiceItemDto(
                i.solutionId,
                i.solutionName,
                COUNT(DISTINCT e.leadId),
                COUNT(DISTINCT i.id),
                SUM(i.grandTotal)
            )
            FROM Invoice i
            JOIN i.unbilledInvoice u
            LEFT JOIN u.estimate e
            WHERE i.isCancelled = false
              AND i.status = :status
              AND i.invoiceDate >= :fromDate
              AND i.invoiceDate <= :toDate
              AND u.createdBy.id = :userId
              AND i.solutionName IS NOT NULL
              AND TRIM(i.solutionName) <> ''
            GROUP BY i.solutionId, i.solutionName
            ORDER BY SUM(i.grandTotal) DESC, COUNT(DISTINCT i.id) DESC
            """)
    List<TopSellingServiceItemDto> findTopSellingServicesForSalesperson(
            @Param("userId") Long userId,
            @Param("status") InvoiceStatus status,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            Pageable pageable
    );


    @Query("""
        SELECT new com.account.dto.dashboard.TopConvertedLeadItemDto(
            i.id,
            i.invoiceNumber,
            c.id,
            c.name,
            cu.id,
            cu.unitName,
            e.leadId,
            i.solutionId,
            i.solutionName,
            i.grandTotal,
            i.invoiceDate
        )
        FROM Invoice i
        JOIN i.unbilledInvoice u
        LEFT JOIN u.company c
        LEFT JOIN u.unit cu
        LEFT JOIN u.estimate e
        WHERE i.isCancelled = false
          AND i.status = :status
          AND i.invoiceDate >= :fromDate
          AND i.invoiceDate <= :toDate
          AND u.createdBy.id = :userId
          AND c.name IS NOT NULL
        ORDER BY i.grandTotal DESC, i.invoiceDate DESC
        """)
    List<TopConvertedLeadItemDto> findTopConvertedLeadsForSalesperson(
            @Param("userId") Long userId,
            @Param("status") InvoiceStatus status,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            Pageable pageable
    );


    @Query("""
            SELECT SUM(i.grandTotal)
            FROM Invoice i
            JOIN i.unbilledInvoice u
            WHERE i.isCancelled = false
              AND i.status = :status
              AND u.createdBy.id = :userId
              AND i.invoiceDate >= :fromDate
              AND i.invoiceDate <= :toDate
            """)
    BigDecimal sumGeneratedRevenueForSalesperson(
            @Param("userId") Long userId,
            @Param("status") InvoiceStatus status,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate
    );

    @Query("""
            SELECT i.invoiceDate, i.grandTotal
            FROM Invoice i
            JOIN i.unbilledInvoice u
            WHERE i.isCancelled = false
              AND i.status = :status
              AND u.createdBy.id = :userId
              AND i.invoiceDate >= :fromDate
              AND i.invoiceDate <= :toDate
            ORDER BY i.invoiceDate ASC
            """)
    List<Object[]> findRevenueTrendRowsForSalesperson(
            @Param("userId") Long userId,
            @Param("status") InvoiceStatus status,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate
    );

    @Query("""
        SELECT new com.account.dto.dashboard.RevenueByServiceItemDto(
            i.solutionId,
            i.solutionName,
            SUM(i.grandTotal),
            COUNT(DISTINCT i.id)
        )
        FROM Invoice i
        JOIN i.unbilledInvoice u
        WHERE i.isCancelled = false
          AND i.status = :status
          AND u.createdBy.id = :userId
          AND i.invoiceDate >= :fromDate
          AND i.invoiceDate <= :toDate
          AND i.solutionName IS NOT NULL
          AND TRIM(i.solutionName) <> ''
        GROUP BY i.solutionId, i.solutionName
        ORDER BY SUM(i.grandTotal) DESC
        """)
    List<RevenueByServiceItemDto> findRevenueByServiceForSalesperson(
            @Param("userId") Long userId,
            @Param("status") InvoiceStatus status,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            Pageable pageable
    );

    @Query("""
        SELECT new com.account.dto.dashboard.TopCompanyItemDto(
            c.id,
            c.name,
            SUM(i.grandTotal),
            COUNT(DISTINCT i.id)
        )
        FROM Invoice i
        JOIN i.unbilledInvoice u
        JOIN u.company c
        WHERE i.isCancelled = false
          AND i.status = :status
          AND u.createdBy.id = :userId
          AND i.invoiceDate >= :fromDate
          AND i.invoiceDate <= :toDate
          AND c.isDeleted = false
          AND c.name IS NOT NULL
          AND TRIM(c.name) <> ''
        GROUP BY c.id, c.name
        ORDER BY SUM(i.grandTotal) DESC
        """)
    List<TopCompanyItemDto> findTopCompaniesForSalesperson(
            @Param("userId") Long userId,
            @Param("status") InvoiceStatus status,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            Pageable pageable
    );


    @Query("""
        SELECT COALESCE(SUM(i.grandTotal), 0)
        FROM Invoice i
        JOIN i.unbilledInvoice u
        WHERE i.isCancelled = false
          AND i.status = :status
          AND u.isCancelled = false
          AND u.approvedBy.id = :userId
          AND i.invoiceDate >= :fromDate
          AND i.invoiceDate <= :toDate
        """)
    BigDecimal sumInvoiceReceivedByUserAndDateRange(
            @Param("userId") Long userId,
            @Param("status") InvoiceStatus status,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate
    );


    @Query(value = """
    SELECT 
        bucket.status AS status,
        COUNT(*) AS count
    FROM (
        SELECT 
            i.id AS invoice_id,
            CASE
                WHEN u.outstanding_amount > 0
                     AND EXISTS (
                        SELECT 1
                        FROM payment_receipt pr
                        WHERE pr.unbilled_invoice_id = u.id
                          AND pr.is_cancelled = false
                          AND pr.next_payment_due_date IS NOT NULL
                          AND pr.next_payment_due_date < CURRENT_DATE()
                     )
                THEN 'OVERDUE'

                WHEN COALESCE(u.received_amount, 0) >= COALESCE(u.total_amount, 0)
                     AND COALESCE(u.total_amount, 0) > 0
                THEN 'PAID'

                WHEN COALESCE(u.received_amount, 0) > 0
                     AND COALESCE(u.received_amount, 0) < COALESCE(u.total_amount, 0)
                THEN 'PARTIALLY_PAID'

                ELSE 'GENERATED'
            END AS status
        FROM invoice i
        JOIN unbilled_invoice u 
            ON u.id = i.unbilled_invoice_id
        WHERE i.is_cancelled = false
          AND u.is_cancelled = false
          AND i.status IN ('GENERATED', 'E_INVOICE_CONFIRMED')
          AND u.updated_by = :userId
          AND i.invoice_date >= :fromDate
          AND i.invoice_date <= :toDate
    ) bucket
    GROUP BY bucket.status
    """, nativeQuery = true)
    List<InvoiceStatusCountProjection> findInvoiceStatusOverviewForSalesperson(
            @Param("userId") Long userId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate
    );


    @Query(value = """
    SELECT
        COUNT(i.id) AS totalCount,
        COALESCE(SUM(i.grand_total), 0) AS totalAmount
    FROM invoice i
    JOIN unbilled_invoice u
        ON u.id = i.unbilled_invoice_id
    WHERE COALESCE(i.is_cancelled, 0) = 0
      AND COALESCE(u.is_cancelled, 0) = 0
      AND i.invoice_date >= :fromDate
      AND i.invoice_date <= :toDate
    """, nativeQuery = true)
    Object[] getTaxInvoiceSummaryForDashboard(
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate
    );


    @Query(value = """
    SELECT
        COUNT(i.id) AS totalCount,
        COALESCE(SUM(i.grand_total), 0) AS totalAmount
    FROM invoice i
    WHERE COALESCE(i.is_cancelled, 0) = 1
      AND i.invoice_date >= :fromDate
      AND i.invoice_date <= :toDate
    """, nativeQuery = true)
    Object[] getCancelledInvoiceSummaryForDashboard(
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate
    );



    Optional<Invoice> findByTriggeringPaymentAndIsCancelledFalse(PaymentReceipt triggeringPayment);

    @Query("""
        select i
        from Invoice i
        where i.estimate.id = :estimateId
          and i.invoiceOrigin = :origin
          and i.isCancelled = false
          and i.paymentStatus in :paymentStatuses
        order by i.invoiceDate asc, i.id asc
        """)
    List<Invoice> findActiveAdvanceInvoicesForUpdate(
            @Param("estimateId") Long estimateId,
            @Param("origin") InvoiceOrigin origin,
            @Param("paymentStatuses") Collection<InvoicePaymentStatus> paymentStatuses
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
       select distinct invoice
       from Invoice invoice
       join fetch invoice.estimate estimate
       left join fetch invoice.advanceTaxInvoiceRequest advanceRequest
       left join fetch estimate.company company
       left join fetch estimate.unit unit
       left join fetch estimate.contact contact
       left join fetch invoice.lineItems lineItem
       where invoice.id = :invoiceId
       """)
    Optional<Invoice> findByIdForAdvanceEInvoiceConfirmation(
            @Param("invoiceId") Long invoiceId
    );

    @Query("""
        SELECT CASE WHEN COUNT(i) > 0 THEN true ELSE false END
        FROM Invoice i
        WHERE LOWER(TRIM(i.eInvoiceIrn)) = LOWER(TRIM(:eInvoiceIrn))
          AND (:excludedInvoiceId IS NULL OR i.id <> :excludedInvoiceId)
          AND i.isCancelled = false
        """)
    boolean existsByEInvoiceIrnExcludingInvoice(
            @Param("eInvoiceIrn") String eInvoiceIrn,
            @Param("excludedInvoiceId") Long excludedInvoiceId
    );


}