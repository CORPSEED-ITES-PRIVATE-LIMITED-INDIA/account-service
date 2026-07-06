package com.account.repository.ledger;

import com.account.domain.ledger.AccountingVoucherEntry;
import com.account.domain.ledger.VoucherStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface AccountingVoucherEntryRepository extends JpaRepository<AccountingVoucherEntry, Long> {

    List<AccountingVoucherEntry> findByVoucher_IdOrderByDisplayOrderAsc(Long voucherId);

    @Query("""
            SELECT e
            FROM AccountingVoucherEntry e
            JOIN FETCH e.voucher v
            JOIN FETCH e.ledger l
            WHERE l.id = :ledgerId
              AND v.status = :status
              AND (:fromDate IS NULL OR v.voucherDate >= :fromDate)
              AND (:toDate IS NULL OR v.voucherDate <= :toDate)
            ORDER BY v.voucherDate ASC, v.id ASC, e.displayOrder ASC, e.id ASC
            """)
    List<AccountingVoucherEntry> findLedgerEntriesForStatement(
            @Param("ledgerId") Long ledgerId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("status") VoucherStatus status
    );

    @Query("""
            SELECT COALESCE(SUM(e.debitAmount), 0)
            FROM AccountingVoucherEntry e
            JOIN e.voucher v
            JOIN e.ledger l
            WHERE l.id = :ledgerId
              AND v.status = :status
              AND v.voucherDate < :fromDate
            """)
    BigDecimal sumDebitBeforeDate(
            @Param("ledgerId") Long ledgerId,
            @Param("fromDate") LocalDate fromDate,
            @Param("status") VoucherStatus status
    );

    @Query("""
            SELECT COALESCE(SUM(e.creditAmount), 0)
            FROM AccountingVoucherEntry e
            JOIN e.voucher v
            JOIN e.ledger l
            WHERE l.id = :ledgerId
              AND v.status = :status
              AND v.voucherDate < :fromDate
            """)
    BigDecimal sumCreditBeforeDate(
            @Param("ledgerId") Long ledgerId,
            @Param("fromDate") LocalDate fromDate,
            @Param("status") VoucherStatus status
    );
}