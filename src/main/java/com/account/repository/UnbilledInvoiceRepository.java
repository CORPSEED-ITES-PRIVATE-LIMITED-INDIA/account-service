package com.account.repository;

import com.account.domain.estimate.Estimate;
import com.account.domain.status.UnbilledStatus;
import com.account.domain.unbilled.UnbilledInvoice;
import com.account.dto.dashboard.MonthlyAmountProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;


@Repository
public interface UnbilledInvoiceRepository extends JpaRepository<UnbilledInvoice, Long> {

    Optional<UnbilledInvoice> findByEstimateAndIsCancelledFalse(Estimate estimate);

    Optional<UnbilledInvoice> findTopByEstimateAndIsCancelledFalseOrderByCreatedAtDesc(Estimate estimate);

    @Query("""
SELECT u
FROM UnbilledInvoice u
WHERE
    (
        (:cancelledFilter = true AND u.status = :cancelledStatus)
        OR
        (:cancelledFilter = false AND u.isCancelled = false)
    )
AND
    (
        :userId IS NULL
        OR u.createdBy.id = :userId
        OR u.approvedBy.id = :userId
    )
AND
    (
        :status IS NULL
        OR u.status = :status
    )
ORDER BY u.createdAt DESC
""")
    Page<UnbilledInvoice> findUnbilledInvoices(
            @Param("userId") Long userId,
            @Param("status") UnbilledStatus status,
            @Param("cancelledFilter") boolean cancelledFilter,
            @Param("cancelledStatus") UnbilledStatus cancelledStatus,
            Pageable pageable
    );

    Optional<UnbilledInvoice> findByEstimateEstimateNumber(String estimateNumber);

    long countByCreatedByIdOrApprovedByIdAndIsCancelledFalse(Long createdById, Long approvedById);

    long countByStatusAndIsCancelledFalse(UnbilledStatus status);

    long countByCreatedByIdOrApprovedByIdAndStatusAndIsCancelledFalse(
            Long createdById,
            Long approvedById,
            UnbilledStatus status
    );

    @Query("""
        SELECT u
        FROM UnbilledInvoice u
        LEFT JOIN u.company c
        LEFT JOIN u.estimate e
        WHERE u.isCancelled = false
          AND (:unbilledNumber IS NULL OR LOWER(u.unbilledNumber) LIKE LOWER(CONCAT('%', :unbilledNumber, '%')))
          AND (:companyName IS NULL OR LOWER(c.name) LIKE LOWER(CONCAT('%', :companyName, '%')))
          AND (:estimateNumber IS NULL OR LOWER(e.estimateNumber) LIKE LOWER(CONCAT('%', :estimateNumber, '%')))
        """)
    Page<UnbilledInvoice> searchUnbilledInvoicesAndIsCancelledFalse(
            @Param("unbilledNumber") String unbilledNumber,
            @Param("companyName") String companyName,
            @Param("estimateNumber") String estimateNumber,
            Pageable pageable
    );

    @Query("""
        SELECT COUNT(u)
        FROM UnbilledInvoice u
        LEFT JOIN u.company c
        LEFT JOIN u.estimate e
        WHERE u.isCancelled = false
          AND (:unbilledNumber IS NULL OR LOWER(u.unbilledNumber) LIKE LOWER(CONCAT('%', :unbilledNumber, '%')))
          AND (:companyName IS NULL OR LOWER(c.name) LIKE LOWER(CONCAT('%', :companyName, '%')))
          AND (:estimateNumber IS NULL OR LOWER(e.estimateNumber) LIKE LOWER(CONCAT('%', :estimateNumber, '%')))
        """)
    long countSearchUnbilledInvoicesAndIsCancelledFalse(
            @Param("unbilledNumber") String unbilledNumber,
            @Param("companyName") String companyName,
            @Param("estimateNumber") String estimateNumber
    );

    List<UnbilledInvoice> findByEstimateIdInAndIsCancelledFalse(List<Long> estimateIds);

    /**
     * Find Unbilled Invoice by Unbilled Number using Native Query
     */
    @Query(value = """
        SELECT * FROM unbilled_invoice 
        WHERE unbilled_number = :unbilledNumber 
          AND is_cancelled = false 
        LIMIT 1
        """,
            nativeQuery = true)
    Optional<UnbilledInvoice> findByUnbilledNumberAndIsCancelledFalse(
            @Param("unbilledNumber") String unbilledNumber
    );

    /**
     * Report API query:
     * Filter unbilled invoices by userId, status, fromDate and toDate.
     *
     * Date filter is based on createdAt.
     */
    @Query("""
    SELECT u
    FROM UnbilledInvoice u
    WHERE u.isCancelled = false

      AND (:userId IS NULL
            OR u.createdBy.id = :userId
            OR u.approvedBy.id = :userId)

      AND (:createdByUserId IS NULL
            OR u.createdBy.id = :createdByUserId)

      AND (:status IS NULL OR u.status = :status)

      AND (:fromDateTime IS NULL OR u.createdAt >= :fromDateTime)

      AND (:toDateTime IS NULL OR u.createdAt < :toDateTime)

    ORDER BY u.createdAt DESC
    """)
    List<UnbilledInvoice> findUnbilledReport(
            @Param("userId") Long userId,
            @Param("createdByUserId") Long createdByUserId,
            @Param("status") UnbilledStatus status,
            @Param("fromDateTime") LocalDateTime fromDateTime,
            @Param("toDateTime") LocalDateTime toDateTime
    );

    @Query("""
    SELECT COUNT(u)
    FROM UnbilledInvoice u
    WHERE u.isCancelled = false

      AND (:userId IS NULL
            OR u.createdBy.id = :userId
            OR u.approvedBy.id = :userId)

      AND (:createdByUserId IS NULL
            OR u.createdBy.id = :createdByUserId)

      AND (:status IS NULL OR u.status = :status)

      AND (:fromDateTime IS NULL OR u.createdAt >= :fromDateTime)

      AND (:toDateTime IS NULL OR u.createdAt < :toDateTime)
    """)
    long countUnbilledReport(
            @Param("userId") Long userId,
            @Param("createdByUserId") Long createdByUserId,
            @Param("status") UnbilledStatus status,
            @Param("fromDateTime") LocalDateTime fromDateTime,
            @Param("toDateTime") LocalDateTime toDateTime
    );


    @Query("""
            SELECT SUM(u.outstandingAmount)
            FROM UnbilledInvoice u
            WHERE u.isCancelled = false
              AND u.createdBy.id = :userId
              AND u.status IN :statuses
              AND u.createdAt >= :fromDateTime
              AND u.createdAt < :toDateTime
              AND u.outstandingAmount > :zeroAmount
            """)
    BigDecimal sumRevenuePipelineForSalesperson(
            @Param("userId") Long userId,
            @Param("statuses") Collection<UnbilledStatus> statuses,
            @Param("fromDateTime") LocalDateTime fromDateTime,
            @Param("toDateTime") LocalDateTime toDateTime,
            @Param("zeroAmount") BigDecimal zeroAmount
    );

    @Query("""
            SELECT COUNT(DISTINCT u.id)
            FROM UnbilledInvoice u
            WHERE u.isCancelled = false
              AND u.createdBy.id = :userId
              AND u.status IN :statuses
              AND u.createdAt >= :fromDateTime
              AND u.createdAt < :toDateTime
              AND u.outstandingAmount > :zeroAmount
            """)
    Long countRevenuePipelineDealsForSalesperson(
            @Param("userId") Long userId,
            @Param("statuses") Collection<UnbilledStatus> statuses,
            @Param("fromDateTime") LocalDateTime fromDateTime,
            @Param("toDateTime") LocalDateTime toDateTime,
            @Param("zeroAmount") BigDecimal zeroAmount
    );


    @Query("""
        SELECT COALESCE(SUM(u.totalAmount), 0)
        FROM UnbilledInvoice u
        WHERE u.isCancelled = false
          AND u.createdBy.id = :userId
          AND u.createdAt >= :fromDateTime
          AND u.createdAt < :toDateTime
        """)
    BigDecimal sumTotalBilledForSalesperson(
            @Param("userId") Long userId,
            @Param("fromDateTime") LocalDateTime fromDateTime,
            @Param("toDateTime") LocalDateTime toDateTime
    );

    @Query("""
        SELECT COALESCE(SUM(u.receivedAmount), 0)
        FROM UnbilledInvoice u
        WHERE u.isCancelled = false
          AND u.createdBy.id = :userId
          AND u.createdAt >= :fromDateTime
          AND u.createdAt < :toDateTime
        """)
    BigDecimal sumReceivedForSalesperson(
            @Param("userId") Long userId,
            @Param("fromDateTime") LocalDateTime fromDateTime,
            @Param("toDateTime") LocalDateTime toDateTime
    );

    @Query("""
        SELECT COALESCE(SUM(u.outstandingAmount), 0)
        FROM UnbilledInvoice u
        WHERE u.isCancelled = false
          AND u.createdBy.id = :userId
          AND u.createdAt >= :fromDateTime
          AND u.createdAt < :toDateTime
        """)
    BigDecimal sumPendingForSalesperson(
            @Param("userId") Long userId,
            @Param("fromDateTime") LocalDateTime fromDateTime,
            @Param("toDateTime") LocalDateTime toDateTime
    );


    @Query("""
            SELECT COALESCE(SUM(u.totalAmount), 0)
            FROM UnbilledInvoice u
            WHERE u.isCancelled = false
              AND u.createdBy.id = :userId
              AND u.createdAt >= :fromDateTime
              AND u.createdAt < :toDateTime
            """)
    BigDecimal sumTotalBilledByUserAndDateRange(
            @Param("userId") Long userId,
            @Param("fromDateTime") LocalDateTime fromDateTime,
            @Param("toDateTime") LocalDateTime toDateTime
    );

    @Query("""
            SELECT COALESCE(SUM(u.outstandingAmount), 0)
            FROM UnbilledInvoice u
            WHERE u.isCancelled = false
              AND u.createdBy.id = :userId
              AND u.createdAt >= :fromDateTime
              AND u.createdAt < :toDateTime
            """)
    BigDecimal sumOutstandingByUserAndDateRange(
            @Param("userId") Long userId,
            @Param("fromDateTime") LocalDateTime fromDateTime,
            @Param("toDateTime") LocalDateTime toDateTime
    );

    @Query("""
            SELECT COUNT(u.id)
            FROM UnbilledInvoice u
            WHERE u.isCancelled = false
              AND u.createdBy.id = :userId
              AND u.status = :status
              AND u.createdAt >= :fromDateTime
              AND u.createdAt < :toDateTime
            """)
    Long countPendingApprovalsByUserAndDateRange(
            @Param("userId") Long userId,
            @Param("status") UnbilledStatus status,
            @Param("fromDateTime") LocalDateTime fromDateTime,
            @Param("toDateTime") LocalDateTime toDateTime
    );

    @Query("""
            SELECT COUNT(u.id)
            FROM UnbilledInvoice u
            WHERE u.isCancelled = false
              AND u.createdBy.id = :userId
              AND u.status = :status
              AND u.createdAt >= :todayStart
              AND u.createdAt < :tomorrowStart
            """)
    Long countPendingApprovalsTodayByUser(
            @Param("userId") Long userId,
            @Param("status") UnbilledStatus status,
            @Param("todayStart") LocalDateTime todayStart,
            @Param("tomorrowStart") LocalDateTime tomorrowStart
    );


    @Query(value = """
    SELECT 
        DATE_FORMAT(u.created_at, '%Y-%m') AS monthKey,
        COALESCE(SUM(u.total_amount), 0) AS amount
    FROM unbilled_invoice u
    WHERE u.is_cancelled = false
      AND u.created_by = :userId
      AND u.created_at >= :fromDateTime
      AND u.created_at < :toDateTime
    GROUP BY DATE_FORMAT(u.created_at, '%Y-%m')
    ORDER BY monthKey
    """, nativeQuery = true)
    List<MonthlyAmountProjection> findMonthlyBilledAmountForSalesperson(
            @Param("userId") Long userId,
            @Param("fromDateTime") LocalDateTime fromDateTime,
            @Param("toDateTime") LocalDateTime toDateTime
    );


    @Query(
            value = """
        SELECT *
        FROM (
            SELECT
                u.id AS itemId,
                'UNBILLED_INVOICE_APPROVAL' AS itemType,
                'Unbilled Invoice Approval' AS title,
                c.name AS subTitle,
                c.id AS companyId,
                c.name AS companyName,
                u.id AS referenceId,
                u.unbilled_number AS referenceNumber,
                CASE
                    WHEN COALESCE(u.current_received_amount, 0) > 0
                    THEN COALESCE(u.current_received_amount, 0)
                    ELSE COALESCE(u.total_amount, 0)
                END AS amount,
                u.status AS sourceStatus,
                'Pending' AS badge,
                'PENDING' AS priority,
                u.created_at AS createdAt
            FROM unbilled_invoice u
            LEFT JOIN company c ON c.id = u.company_id
            WHERE u.is_cancelled = 0
              AND u.status = 'PENDING_APPROVAL'
              AND u.created_by = :userId
              AND u.created_at >= :fromDateTime
              AND u.created_at < :toDateTime

            UNION ALL

            SELECT
                u.id AS itemId,
                'CANCEL_REQUEST' AS itemType,
                'Cancel Request' AS title,
                c.name AS subTitle,
                c.id AS companyId,
                c.name AS companyName,
                u.id AS referenceId,
                u.unbilled_number AS referenceNumber,
                COALESCE(u.total_amount, 0) AS amount,
                u.status AS sourceStatus,
                'Urgent' AS badge,
                'URGENT' AS priority,
                COALESCE(u.updated_at, u.created_at) AS createdAt
            FROM unbilled_invoice u
            LEFT JOIN company c ON c.id = u.company_id
            WHERE u.is_cancelled = 0
              AND u.status = 'CANCEL_REQUESTED'
              AND u.created_by = :userId
              AND COALESCE(u.updated_at, u.created_at) >= :fromDateTime
              AND COALESCE(u.updated_at, u.created_at) < :toDateTime

            UNION ALL

            SELECT
                p.id AS itemId,
                'PAYMENT_RECEIPT_VERIFICATION' AS itemType,
                'Payment Receipt Verification' AS title,
                c.name AS subTitle,
                c.id AS companyId,
                c.name AS companyName,
                u.id AS referenceId,
                u.unbilled_number AS referenceNumber,
                COALESCE(p.amount, 0) AS amount,
                p.status AS sourceStatus,
                'Review' AS badge,
                'REVIEW' AS priority,
                p.created_at AS createdAt
            FROM payment_receipt p
            JOIN unbilled_invoice u ON u.id = p.unbilled_invoice_id
            LEFT JOIN company c ON c.id = u.company_id
            WHERE p.is_cancelled = 0
              AND u.is_cancelled = 0
              AND p.status = 'PENDING'
              AND u.created_by = :userId
              AND p.created_at >= :fromDateTime
              AND p.created_at < :toDateTime

            UNION ALL

            SELECT
                t.id AS itemId,
                'TDS_REGISTRATION' AS itemType,
                'TDS Registration' AS title,
                c.name AS subTitle,
                c.id AS companyId,
                c.name AS companyName,
                u.id AS referenceId,
                u.unbilled_number AS referenceNumber,
                COALESCE(t.tds_amount, 0) AS amount,
                t.status AS sourceStatus,
                'Pending' AS badge,
                'PENDING' AS priority,
                t.created_at AS createdAt
            FROM tds_registration t
            JOIN unbilled_invoice u ON u.id = t.unbilled_invoice_id
            LEFT JOIN company c ON c.id = t.company_id
            WHERE t.is_deleted = 0
              AND u.is_cancelled = 0
              AND t.status = 'PENDING'
              AND u.created_by = :userId
              AND t.created_at >= :fromDateTime
              AND t.created_at < :toDateTime
        ) approvalQueue
        ORDER BY approvalQueue.createdAt DESC
        """,
            countQuery = """
        SELECT COUNT(*)
        FROM (
            SELECT u.id
            FROM unbilled_invoice u
            WHERE u.is_cancelled = 0
              AND u.status = 'PENDING_APPROVAL'
              AND u.created_by = :userId
              AND u.created_at >= :fromDateTime
              AND u.created_at < :toDateTime

            UNION ALL

            SELECT u.id
            FROM unbilled_invoice u
            WHERE u.is_cancelled = 0
              AND u.status = 'CANCEL_REQUESTED'
              AND u.created_by = :userId
              AND COALESCE(u.updated_at, u.created_at) >= :fromDateTime
              AND COALESCE(u.updated_at, u.created_at) < :toDateTime

            UNION ALL

            SELECT p.id
            FROM payment_receipt p
            JOIN unbilled_invoice u ON u.id = p.unbilled_invoice_id
            WHERE p.is_cancelled = 0
              AND u.is_cancelled = 0
              AND p.status = 'PENDING'
              AND u.created_by = :userId
              AND p.created_at >= :fromDateTime
              AND p.created_at < :toDateTime

            UNION ALL

            SELECT t.id
            FROM tds_registration t
            JOIN unbilled_invoice u ON u.id = t.unbilled_invoice_id
            WHERE t.is_deleted = 0
              AND u.is_cancelled = 0
              AND t.status = 'PENDING'
              AND u.created_by = :userId
              AND t.created_at >= :fromDateTime
              AND t.created_at < :toDateTime
        ) approvalQueueCount
        """,
            nativeQuery = true
    )
    Page<Object[]> findApprovalQueueForDashboard(
            @Param("userId") Long userId,
            @Param("fromDateTime") LocalDateTime fromDateTime,
            @Param("toDateTime") LocalDateTime toDateTime,
            Pageable pageable
    );

    @Query(value = """
    SELECT COUNT(*)
    FROM unbilled_invoice u
    WHERE u.is_cancelled = 0
      AND u.status = 'CANCEL_REQUESTED'
      AND u.created_by = :userId
      AND COALESCE(u.updated_at, u.created_at) >= :fromDateTime
      AND COALESCE(u.updated_at, u.created_at) < :toDateTime
    """, nativeQuery = true)
    Long countUrgentApprovalQueueForDashboard(
            @Param("userId") Long userId,
            @Param("fromDateTime") LocalDateTime fromDateTime,
            @Param("toDateTime") LocalDateTime toDateTime
    );

}