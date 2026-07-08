package com.account.repository;

import com.account.domain.PaymentReceipt;
import com.account.domain.unbilled.UnbilledInvoice;
import com.account.dto.dashboard.MonthlyAmountProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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



}