package com.account.domain.ledger;

import com.account.domain.User;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "accounting_voucher",
        indexes = {
                @Index(name = "idx_voucher_number_unique", columnList = "voucher_number", unique = true),
                @Index(name = "idx_voucher_type", columnList = "voucher_type"),
                @Index(name = "idx_voucher_date", columnList = "voucher_date"),
                @Index(name = "idx_voucher_source", columnList = "source_type, source_id"),
                @Index(name = "idx_voucher_status", columnList = "status")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountingVoucher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Example: RCP-2026-000001, INV-VCH-2026-000001
    @Column(name = "voucher_number", nullable = false, unique = true, length = 50)
    private String voucherNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "voucher_type", nullable = false, length = 50)
    private VoucherType voucherType;

    @Column(name = "voucher_date", nullable = false)
    private LocalDate voucherDate = LocalDate.now();

    // Example: PAYMENT_RECEIPT, INVOICE, CREDIT_NOTE, REFUND
    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 50)
    private VoucherSourceType sourceType;

    // Example: paymentReceiptId / invoiceId / creditNoteId
    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private VoucherStatus status = VoucherStatus.POSTED;

    @Column(name = "total_debit", precision = 15, scale = 2, nullable = false)
    private BigDecimal totalDebit = BigDecimal.ZERO;

    @Column(name = "total_credit", precision = 15, scale = 2, nullable = false)
    private BigDecimal totalCredit = BigDecimal.ZERO;

    @Column(columnDefinition = "TEXT")
    private String narration;

    @OneToMany(
            mappedBy = "voucher",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @Builder.Default
    private List<AccountingVoucherEntry> entries = new ArrayList<>();

    @CreatedBy
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", updatable = false)
    private User createdBy;

    @LastModifiedBy
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private User updatedBy;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    public void addEntry(AccountingVoucherEntry entry) {
        entries.add(entry);
        entry.setVoucher(this);
    }

    public void calculateTotals() {
        this.totalDebit = entries.stream()
                .map(AccountingVoucherEntry::getDebitAmount)
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        this.totalCredit = entries.stream()
                .map(AccountingVoucherEntry::getCreditAmount)
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (this.totalDebit.compareTo(this.totalCredit) != 0) {
            throw new IllegalStateException("Voucher debit and credit amount must be equal");
        }
    }

    @PrePersist
    @PreUpdate
    public void beforeSave() {
        calculateTotals();
    }
}