package com.account.repository;

import com.account.domain.UnbilledInvoice;
import com.account.domain.UnbilledStatus;
import com.account.domain.estimate.Estimate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
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
            (:cancelledStatus = true AND u.status = com.account.domain.UnbilledStatus.CANCELLED)
            OR
            (:cancelledStatus = false AND u.isCancelled = false)
        )
    AND
        (:userId IS NULL
            OR u.createdBy.id = :userId
            OR u.approvedBy.id = :userId)
    AND
        (:status IS NULL OR u.status = :status)
    """)
    Page<UnbilledInvoice> findUnbilledInvoices(
            @Param("userId") Long userId,
            @Param("status") UnbilledStatus status,
            @Param("cancelledStatus") boolean cancelledStatus,
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
}