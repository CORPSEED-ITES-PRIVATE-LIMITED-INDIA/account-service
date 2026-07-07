package com.account.repository;

import com.account.domain.invoice.Invoice;
import com.account.domain.status.InvoiceStatus;
import com.account.domain.PaymentReceipt;
import com.account.dto.dashboard.RevenueByServiceItemDto;
import com.account.dto.dashboard.TopConvertedLeadItemDto;
import com.account.dto.dashboard.TopSellingServiceItemDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
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




}