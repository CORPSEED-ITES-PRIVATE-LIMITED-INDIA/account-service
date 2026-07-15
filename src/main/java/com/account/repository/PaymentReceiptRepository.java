package com.account.repository;

import com.account.domain.PaymentReceipt;
import com.account.domain.status.PaymentStatus;
import com.account.domain.unbilled.UnbilledInvoice;
import com.account.dto.dashboard.MonthlyAmountProjection;
import com.account.dto.dashboard.RecentPaymentItemDto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentReceiptRepository extends JpaRepository<PaymentReceipt, Long> {

    Optional<PaymentReceipt> findTopByUnbilledInvoiceAndIsCancelledFalseOrderByIdAsc(UnbilledInvoice unbilledInvoice);

    @Query(value = """
    SELECT 
        DATE_FORMAT(p.payment_date, '%Y-%m') AS monthKey,
        COALESCE(SUM(p.amount), 0) AS amount
    FROM payment_receipt p
    JOIN unbilled_invoice u 
        ON u.id = p.unbilled_invoice_id
    WHERE p.is_cancelled = false
      AND u.is_cancelled = false
      AND p.status = 'APPROVED'
      AND u.created_by = :userId
      AND p.payment_date >= :fromDate
      AND p.payment_date <= :toDate
    GROUP BY DATE_FORMAT(p.payment_date, '%Y-%m')
    ORDER BY monthKey
    """, nativeQuery = true)
    List<MonthlyAmountProjection> findMonthlyCollectionAmountForSalesperson(
            @Param("userId") Long userId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate
    );


    @Query(
            value = """
        SELECT new com.account.dto.dashboard.RecentPaymentItemDto(
            p.id,
            c.id,
            c.name,
            p.amount,
            p.paymentDate,
            p.paymentMode,
            p.status,
           cast(null as string)
        )
        FROM PaymentReceipt p
        JOIN p.unbilledInvoice u
        LEFT JOIN u.company c
        WHERE p.isCancelled = false
          AND u.isCancelled = false
          AND u.createdBy.id = :userId
          AND p.paymentDate >= :fromDate
          AND p.paymentDate <= :toDate
          AND (:status IS NULL OR p.status = :status)
        ORDER BY p.paymentDate DESC, p.id DESC
        """,
            countQuery = """
        SELECT COUNT(p)
        FROM PaymentReceipt p
        JOIN p.unbilledInvoice u
        WHERE p.isCancelled = false
          AND u.isCancelled = false
          AND u.createdBy.id = :userId
          AND p.paymentDate >= :fromDate
          AND p.paymentDate <= :toDate
          AND (:status IS NULL OR p.status = :status)
        """
    )
    Page<RecentPaymentItemDto> findRecentPaymentsForDashboard(
            @Param("userId") Long userId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("status") PaymentStatus status,
            Pageable pageable
    );


    @Query(value = """
    SELECT
        COUNT(p.id) AS totalCount,
        COALESCE(SUM(p.amount), 0) AS totalAmount
    FROM payment_receipt p
    JOIN unbilled_invoice u
        ON u.id = p.unbilled_invoice_id
    WHERE p.is_cancelled = 0
      AND u.is_cancelled = 0
      AND u.created_by = :userId
      AND p.payment_date >= :fromDate
      AND p.payment_date <= :toDate
    """, nativeQuery = true)
    Object[] getAdvancePaymentSummaryForDashboard(
            @Param("userId") Long userId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate
    );


}