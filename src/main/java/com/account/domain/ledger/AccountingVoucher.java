package com.account.domain.ledger;

import com.account.domain.User;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "accounting_voucher",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_voucher_source_type_source_id",
                        columnNames = {"source_type", "source_id"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_voucher_number_unique",
                        columnList = "voucher_number",
                        unique = true
                ),
                @Index(name = "idx_voucher_type", columnList = "voucher_type"),
                @Index(name = "idx_voucher_date", columnList = "voucher_date"),
                @Index(name = "idx_voucher_status", columnList = "status"),
                @Index(name = "idx_voucher_project_id", columnList = "project_id"),
                @Index(name = "idx_voucher_client_company_id", columnList = "client_company_id"),
                @Index(name = "idx_voucher_party_ledger_id", columnList = "party_ledger_id")
        }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountingVoucher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "voucher_number", nullable = false, unique = true, length = 50)
    private String voucherNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "voucher_type", nullable = false, length = 50)
    private VoucherType voucherType;

    @Column(name = "voucher_date", nullable = false)
    private LocalDate voucherDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 50)
    private VoucherSourceType sourceType;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private VoucherStatus status;

    @Column(name = "total_debit", precision = 15, scale = 2, nullable = false)
    private BigDecimal totalDebit;

    @Column(name = "total_credit", precision = 15, scale = 2, nullable = false)
    private BigDecimal totalCredit;

    @Column(name = "narration", columnDefinition = "TEXT")
    private String narration;

    // Operation/project snapshot. These values must remain visible even if
    // project/client master data changes later.
    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "project_no", length = 100)
    private String projectNo;

    @Column(name = "project_name", length = 255)
    private String projectName;

    @Column(name = "client_company_id")
    private Long clientCompanyId;

    @Column(name = "client_company_name", length = 255)
    private String clientCompanyName;

    @Column(name = "client_unit_id")
    private Long clientUnitId;

    @Column(name = "client_unit_name", length = 255)
    private String clientUnitName;

    @Column(name = "expense_paid_by", length = 30)
    private String expensePaidBy;

    // Customer/party ledger displayed on the debit note.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "party_ledger_id")
    private LedgerMaster partyLedger;

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
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (voucherDate == null) {
            voucherDate = LocalDate.now();
        }
        if (status == null) {
            status = VoucherStatus.POSTED;
        }
        if (totalDebit == null) {
            totalDebit = BigDecimal.ZERO;
        }
        if (totalCredit == null) {
            totalCredit = BigDecimal.ZERO;
        }
    }

    public void addEntry(AccountingVoucherEntry entry) {
        if (entry == null) {
            return;
        }
        entries.add(entry);
        entry.setVoucher(this);
    }

    public void calculateTotals() {
        totalDebit = entries.stream()
                .map(AccountingVoucherEntry::getDebitAmount)
                .filter(value -> value != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        totalCredit = entries.stream()
                .map(AccountingVoucherEntry::getCreditAmount)
                .filter(value -> value != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalDebit.compareTo(totalCredit) != 0) {
            throw new IllegalStateException(
                    "Voucher debit and credit amounts must be equal"
            );
        }
    }
}