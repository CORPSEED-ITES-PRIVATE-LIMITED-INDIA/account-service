package com.account.domain.ledger;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(
        name = "accounting_voucher_entry",
        indexes = {
                @Index(name = "idx_voucher_entry_voucher_id", columnList = "voucher_id"),
                @Index(name = "idx_voucher_entry_ledger_id", columnList = "ledger_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountingVoucherEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "voucher_id", nullable = false)
    private AccountingVoucher voucher;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ledger_id", nullable = false)
    private LedgerMaster ledger;

    @Column(name = "debit_amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal debitAmount = BigDecimal.ZERO;

    @Column(name = "credit_amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal creditAmount = BigDecimal.ZERO;

    @Column(columnDefinition = "TEXT")
    private String narration;

    @Column(name = "display_order")
    private Integer displayOrder;

    @PrePersist
    @PreUpdate
    public void validateEntry() {
        BigDecimal debit = debitAmount == null ? BigDecimal.ZERO : debitAmount;
        BigDecimal credit = creditAmount == null ? BigDecimal.ZERO : creditAmount;

        if (debit.compareTo(BigDecimal.ZERO) < 0 || credit.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException("Debit/Credit cannot be negative");
        }

        if (debit.compareTo(BigDecimal.ZERO) > 0 && credit.compareTo(BigDecimal.ZERO) > 0) {
            throw new IllegalStateException("One entry cannot have both debit and credit amount");
        }

        if (debit.compareTo(BigDecimal.ZERO) == 0 && credit.compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalStateException("Either debit or credit amount is required");
        }
    }
}